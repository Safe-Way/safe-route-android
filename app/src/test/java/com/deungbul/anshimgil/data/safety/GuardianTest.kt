package com.deungbul.anshimgil.data.safety

// 시험에 쓰는 도구들
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Assert.assertNull      // assertNull(값): 값이 null(없음)이어야 통과
import org.junit.Assert.assertTrue      // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나
import com.deungbul.anshimgil.data.route.LatLon
import org.junit.Assert.assertFalse     // assertFalse(조건): 조건이 거짓이어야 통과
import java.util.Locale

/**
 * 귀가 지킴이(보호자 알림) 시험.
 *
 * 걷는 사람이 SOS를 누르거나 정한 길에서 벗어나면, 앱은 등록된 보호자(최대 3명)에게 보낼 문자를 만든다.
 * 문자에는 앱 이름, 무슨 일인지, 그리고 지금 위치를 여는 카카오맵 링크가 들어간다.
 * 위치는 이 문자 안에만 담겨 폰 밖으로 나가고, 문자는 걷는 사람이 직접 보낸다.
 *
 * 여기서는 진짜 GPS나 문자 전송을 쓰지 않는다. 위치(위도·경도)를 직접 만들어 앱의 문자 만들기 함수
 * guardianMessage()에 넣고, 돌려받은 문자 글에 필요한 내용이 들어 있는지만 본다.
 * guardianMessage(이유, 위치)는 보낼 문자 전체를 글(String)로 돌려준다. 위치가 null(모름)이면
 * 링크 대신 "지금 위치는 찾지 못했어요."를 넣는다.
 */
class GuardianTest {
    /** 역할: 폰에 저장한 보호자(이름·전화번호)를 다시 읽으면 똑같이 돌아오고, 전화번호가 빠진 기록은 버리는지 확인한다. */
    @Test
    fun storedGuardianComesBackTheSame() {
        val mom = Guardian("엄마", "01012345678")
        assertEquals(mom, Guardian.decode(mom.encode()))
        assertNull(Guardian.decode("이름만"))
    }

    /** 역할: 보호자 전화번호를 입력하면 숫자만 남기고(+82의 +는 유지), 전화번호가 될 수 없는 입력은 거르는지 확인한다. */
    @Test
    fun phoneNumbersKeepOnlyDigits() {
        assertEquals("01012345678", Guardian.cleanPhone("010-1234-5678"))
        assertEquals("+821012345678", Guardian.cleanPhone("+82 10 1234 5678"))
        assertNull(Guardian.cleanPhone("12"))
        assertNull(Guardian.cleanPhone("abc"))
    }

    /**
     * 역할: SOS 때 보호자에게 가는 문자에 앱 이름과 지금 위치의 카카오맵 링크가 바르게 들어가는지 확인한다.
     * 위치를 모를 때(null)도 길 벗어남 알림 문자는 만들어져야 한다.
     */
    @Test
    fun messageCarriesAMapLinkToTheWalker() {
        // SOS 문자를 만든다. 위치는 가짜로 넣은 광주 동구 근처 좌표(위도 35.1404, 경도 126.9318)다.
        val text = guardianMessage(GuardianAlert.SOS, LatLon(35.1404, 126.9318))
        // 문자 맨 앞은 앱 이름 [세잎지도]와 도움 요청 문장으로 시작해야 한다.
        assertTrue(text.startsWith("[세잎지도] 도움이 필요해요!"))
        // 카카오맵 링크가 들어 있어야 한다. 좌표는 소수점 아래 6자리로 맞춰 쓴다(35.1404 → 35.140400).
        // 소수점은 폰 언어 설정과 상관없이 항상 점(.)으로 쓰므로 링크가 깨지지 않는다.
        assertTrue(text.contains("https://map.kakao.com/link/map/지금위치,35.140400,126.931800"))
        // 길 벗어남 문자를 위치 없이(null) 만들어도 "정한 길에서 벗어났어요" 문장은 들어가야 한다.
        assertTrue(guardianMessage(GuardianAlert.OFF_ROUTE, null).contains("정한 길에서 벗어났어요"))
    }


    /** 역할: 보호자 이름에 저장 구분 글자 '|'가 들어가도, 저장했다 다시 읽을 때 전화번호가 깨지지 않는지 확인한다. */
    @Test
    fun aBarInTheNameDoesNotBreakTheNumber() {
        val g = Guardian("엄|마", "01012345678")            // 이름에 '|'가 들어간 보호자
        val back = Guardian.decode(g.encode())!!            // 저장 글자로 바꿨다가 다시 읽기
        assertEquals("01012345678", back.phone)             // 전화번호는 그대로
        assertEquals("엄 마", back.name)                     // '|'는 빈칸으로 바뀌어 저장된다
    }

    /** 역할: 저장된 글자가 깨져 이름이나 번호가 비어 있으면, 그 보호자를 버리고(null) 엉뚱한 번호로 문자가 가지 않는지 확인한다. */
    @Test
    fun brokenSavedTextIsSkipped() {
        assertNull(Guardian.decode(""))              // 아무것도 없음
        assertNull(Guardian.decode("|01012345678"))  // 이름 없음
        assertNull(Guardian.decode("엄마|"))          // 번호 없음
        assertNull(Guardian.decode("엄마|   "))       // 번호가 빈칸뿐
    }

    /** 역할: 위치를 모를 때 SOS 문자는 링크 없이 "지금 위치는 찾지 못했어요."라고 솔직하게 적는지 확인한다. */
    @Test
    fun withoutAFixTheTextSaysSoAndHasNoLink() {
        val text = guardianMessage(GuardianAlert.SOS, null)
        assertTrue(text.startsWith("[세잎지도] 도움이 필요해요!"))   // 무슨 일인지는 그대로 알린다
        assertTrue(text.contains("지금 위치는 찾지 못했어요."))      // 위치를 모른다고 적는다
        assertFalse(text.contains("map.kakao.com"))                 // 엉뚱한 곳을 가리키는 링크는 없다
    }

    /**
     * 역할: 폰 언어가 독일어처럼 소수점을 쉼표로 쓰는 나라여도, 지도 링크의 좌표는 점(35.140400)으로 적히는지 확인한다.
     * 쉼표로 적히면 "35,140400,126,931800"이 되어 링크가 엉뚱한 곳을 가리킨다.
     */
    @Test
    fun theMapLinkUsesDotsWhateverThePhoneLanguage() {
        val before = Locale.getDefault()             // 원래 언어 설정 기억
        try {
            Locale.setDefault(Locale.GERMANY)        // 소수점을 쉼표로 쓰는 언어로 바꿈
            val text = guardianMessage(GuardianAlert.SOS, LatLon(35.1404, 126.9318))
            assertTrue(text, text.contains("지금위치,35.140400,126.931800"))
        } finally {
            Locale.setDefault(before)                // 다른 시험에 영향이 없게 되돌림
        }
    }
}
