package com.deungbul.anshimgil.data.safety

// 시험에 쓰는 도구들
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Assert.assertFalse     // assertFalse(조건): 조건이 거짓이어야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나
import com.deungbul.anshimgil.data.guide.metersBetween
import com.deungbul.anshimgil.data.place.Place
import com.deungbul.anshimgil.data.route.DataQuality
import com.deungbul.anshimgil.data.route.Explanation
import com.deungbul.anshimgil.data.route.LatLon
import com.deungbul.anshimgil.data.route.RouteResult
import com.deungbul.anshimgil.data.route.night
import com.deungbul.anshimgil.data.user.UserMode

/**
 * 이동 기록 시험.
 *
 * 걷는 사람이 "기록해도 좋아요"라고 답한 경우에만, 앱은 길안내를 시작할 때 서버(POST /api/me/trips)에
 * "어디로, 어떻게 가는지"를 보낸다. AI 비서가 "8시 반에 문화전당 가시죠?"처럼 자주 가는 길을 권하려고 쓴다.
 * 개인정보를 지키려고 출발지와 걸은 길은 보내지 않고, 목적지 좌표도 약 100m 단위로 반올림해서 보낸다.
 *
 * 여기서는 진짜 서버를 부르지 않는다. 앱이 서버에 보낼 내용(JSON)을 만드는 함수 tripStartJson()을
 * 직접 부르고, 만들어진 JSON에 무엇이 들어 있고 무엇이 빠져 있는지만 본다.
 * tripStartJson()은 guestId, toName, toLat, toLon, mode, travel, routeKind, weather, night, plannedMin
 * 칸을 가진 JSONObject를 돌려준다.
 */
class TripLogTest {
    /**
     * 시험에 쓰는 목적지: 국립아시아문화전당(광주 동구).
     * Place(아이디, 이름, 주소, 위도, 경도, 사용자와의 거리)이며, 거리는 모른다는 뜻으로 null을 넣었다.
     */
    private val asiaCulture = Place("1", "국립아시아문화전당", "광주 동구 문화전당로 38", 35.146734, 126.920318, null)

    /**
     * 역할(개인정보): 길안내 시작 기록에 출발지와 걸은 길이 절대 들어가지 않고, 목적지는 약 100m 단위로 반올림되는지 확인한다.
     * 누가 실수로 출발지를 넣으면 이 시험이 실패한다.
     */
    @Test
    fun startBodyRoundsTheDestinationAndHasNoStart() {
        // 휠체어 사용자(UserMode.DISABLED)가 문화전당까지 걸어서(transit = false) '안전한 길'로 가는 상황.
        // 비가 오고(weather = "rain"), 밤이 아니며(night = false), 예상 시간은 12분이다.
        val json = tripStartJson("guest-1", asiaCulture, UserMode.DISABLED, transit = false, routeKind = "safe", weather = "rain", night = false, plannedMin = 12)
        assertEquals("guest-1", json.getString("guestId"))              // 손님 번호가 그대로 들어감
        assertEquals("국립아시아문화전당", json.getString("toName"))      // 목적지 이름이 그대로 들어감
        // 위도 35.146734 → 소수점 셋째 자리로 반올림 → 35.147 (오차 0.0까지 정확히 같아야 함)
        assertEquals(35.147, json.getDouble("toLat"), 0.0)
        // 경도 126.920318 → 소수점 셋째 자리로 반올림 → 126.920
        assertEquals(126.920, json.getDouble("toLon"), 0.0)
        assertEquals("wheelchair", json.getString("mode"))              // DISABLED 모드는 서버 말로 "wheelchair"
        assertEquals("walk", json.getString("travel"))                  // 대중교통이 아니므로 "walk"
        assertEquals("safe", json.getString("routeKind"))               // 고른 길 종류가 그대로 들어감
        assertEquals(12, json.getInt("plannedMin"))                     // 예상 시간 12분이 그대로 들어감
        // Never the start or the path.
        // 출발지(origin, fromLat)나 걸은 길(path, geometry) 칸이 하나라도 있으면 실패한다.
        assertFalse(json.has("origin") || json.has("fromLat") || json.has("path") || json.has("geometry"))
        // Rounding moves the point by at most about 60 m.
        // 원래 좌표와 반올림한 좌표 사이의 직선거리(m)를 재서 80m보다 가까운지 본다(이 좌표에서는 약 40m).
        // assert는 Kotlin 기본 검사로, 조건이 거짓이면 시험이 실패한다.
        assert(metersBetween(LatLon(asiaCulture.lat, asiaCulture.lon), LatLon(json.getDouble("toLat"), json.getDouble("toLon"))) < 80)
    }

    /** 역할: 대중교통으로 가는 기록은 travel이 "transit"이고, 길 종류·예상 시간이 없으면 빈 값(null)으로, 밤 여부는 그대로 들어가는지 확인한다. */
    @Test
    fun transitHasNoRouteKind() {
        val json = tripStartJson("g", asiaCulture, UserMode.SENIOR, transit = true, routeKind = null, weather = "clear", night = true, plannedMin = null)
        assertEquals("transit", json.getString("travel"))
        assert(json.isNull("routeKind") && json.isNull("plannedMin"))
        assert(json.getBoolean("night"))
    }

    /** 역할: 길 찾기 결과의 날씨를 비("rain")·폭염("heat")·맑음("clear") 같은 기록용 낱말로 바르게 바꾸는지 확인한다. */
    @Test
    fun weatherWord() {
        val base = RouteResult(emptyList(), emptyList(), Explanation("", emptyList()), emptyList(), DataQuality(true, null))
        assertEquals("rain", tripWeather(base.copy(raining = true)))
        assertEquals("heat", tripWeather(base.copy(weather = listOf("폭염", "33°C"))))
        assertEquals("clear", tripWeather(base))
        assertEquals("clear", tripWeather(null))
    }


    /** 역할: 좌표 반올림이 소수점 셋째 자리(약 100m)로 되고, 원래 자리에서 반 칸(0.0005도, 약 55m) 넘게 움직이지 않는지 확인한다. 남쪽·서쪽(음수) 좌표도 같다. */
    @Test
    fun roundingStaysWithinHalfAStep() {
        assertEquals(35.140, roundTo100m(35.14049), 0.0)     // 0.00049는 버림
        assertEquals(35.141, roundTo100m(35.14051), 0.0)     // 0.00051은 올림
        assertEquals(-0.001, roundTo100m(-0.0006), 0.0)      // 음수도 가까운 쪽으로
        for (v in listOf(35.1404, 126.93189, 35.0, 126.9999)) {
            assertFalse("$v", kotlin.math.abs(roundTo100m(v) - v) > 0.0005 + 1e-9)  // 반 칸 넘게 움직이면 실패
        }
    }

    /** 역할: 서버가 한파라고 하면 기록의 날씨 낱말이 "cold"가 되는지 확인한다(비·폭염은 weatherWord에서 봄). */
    @Test
    fun coldWeatherWord() {
        val base = RouteResult(emptyList(), emptyList(), Explanation("", emptyList()), emptyList(), DataQuality(true, null))
        assertEquals("cold", tripWeather(base.copy(weather = listOf("한파", "-12°C"))))
    }
}
