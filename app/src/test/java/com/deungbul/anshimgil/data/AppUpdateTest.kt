package com.deungbul.anshimgil.data

// 시험에 쓰는 도구들
import org.json.JSONObject              // 서버 답(JSON)을 코드로 만들 때 씀
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Assert.assertNull      // assertNull(값): 값이 null(없음)이어야 통과
import org.junit.Assert.assertTrue      // assertTrue(조건): 조건이 참이어야 통과
import org.junit.Assert.assertFalse     // assertFalse(조건): 조건이 거짓이어야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나

/**
 * 앱 업데이트 안내 창 시험.
 *
 * 앱은 켜질 때 서버(GET /api/app/version)에 "이 버전 업데이트해야 해?"라고 묻는다.
 * 서버 답의 update 값에 따라 창이 달라진다.
 * - "required": 꼭 업데이트. 닫을 수 없는 창
 * - "optional": 업데이트 권장. [나중에] 버튼이 있는 창
 * - "none": 창 없음
 *
 * 여기서는 진짜 서버를 부르지 않는다. 서버 답을 직접 만들어 앱의 읽기 함수
 * parseAppUpdate()에 넣고, 나온 결과(창을 띄울지, 어떤 창인지)가 맞는지만 본다.
 * parseAppUpdate()는 창을 띄워야 하면 AppUpdate를, 띄우지 말아야 하면 null을 돌려준다.
 */
class AppUpdateTest {
    /**
     * 가짜 서버 답을 만드는 도우미 함수.
     * 서버가 실제로 보내는 모양과 같은 JSON을 만든다.
     * 시험마다 바꾸고 싶은 칸(update, url, message)만 골라 넣고, 나머지는 기본값을 쓴다.
     * 예: answer("optional") → update만 "optional"이고 나머지는 기본값인 답
     */
    private fun answer(update: String, url: String = "https://example.com/seipjido.apk", message: String = "새 버전이 나왔어요…") =
        JSONObject(
            // 서버 답 모양: 최신 버전 번호 5, 이름 "1.4", 최소 버전 4, 안내 문구, 내려받을 주소, 업데이트 종류
            // $message, $url, $update 자리에는 위 함수에 넘긴 값이 들어간다.
            """{"latestVersionCode":5,"latestVersionName":"1.4","minVersionCode":4,"message":"$message","downloadUrl":"$url","update":"$update"}""",
        )

    /** 역할: 서버가 "required"(꼭 업데이트)라고 하면, 닫을 수 없는 창에 서버가 보낸 내용이 그대로 담기는지 확인한다. */
    @Test fun requiredCannotBeClosed() {
        // "required" 답을 앱의 읽기 함수에 넣는다. 끝의 !!는 "결과가 꼭 있어야 한다(null이면 바로 실패)"는 뜻.
        val u = parseAppUpdate(answer("required"))!!
        assertTrue(u.required)                                               // 꼭 업데이트 창(닫을 수 없음)이어야 함
        assertEquals("새 버전이 나왔어요…", u.message)                         // 서버가 보낸 안내 문구가 그대로 들어감
        assertEquals("https://example.com/seipjido.apk", u.downloadUrl)      // [업데이트] 버튼이 열 주소가 그대로 들어감
        assertEquals("1.4", u.versionName)                                   // 새 버전 이름도 그대로 들어감
    }

    /** 역할: 서버가 "optional"(권장)이라고 하면, 닫을 수 있는 창([나중에] 버튼 있음)이 되는지 확인한다. */
    @Test fun optionalCanWait() {
        // required가 false = 꼭 업데이트가 아님 = [나중에]를 눌러 닫을 수 있음
        assertFalse(parseAppUpdate(answer("optional"))!!.required)
    }

    /** 역할: 서버가 "none"(업데이트 필요 없음)이라고 하면 창을 띄우지 않는지 확인한다. */
    @Test fun noneShowsNothing() {
        // 결과가 null = 창을 띄우지 않음
        assertNull(parseAppUpdate(answer("none")))
    }

    /**
     * 역할: 비었거나 약속에 없는 값, 주소가 없거나 http(s)가 아닌 주소가 오면 업데이트 창을 띄우지 않는지 확인한다.
     * 닫을 수 없는 '꼭 업데이트' 창에 갇히거나, 버튼이 엉뚱한 앱을 여는 것을 막는다.
     */
    @Test fun oddAnswersShowNothing() {
        assertNull(parseAppUpdate(JSONObject()))                                 // 아무것도 없는 빈 답 → 창 없음
        assertNull(parseAppUpdate(answer("REQUIRED")))                           // 대문자 등 약속에 없는 값 → 모르는 답으로 보고 창 없음
        assertNull(parseAppUpdate(answer("required", url = "")))                 // 내려받을 주소가 없음 → 버튼을 눌러도 갈 곳이 없어 창 없음
        assertNull(parseAppUpdate(answer("required", url = "intent://evil")))    // http(s)가 아닌 주소 → 다른 앱을 열 수 있어 위험하니 창 없음
    }

    /** 역할: 서버가 안내 문구를 비워 보내면, 빈 창 대신 기본 문구 "새 버전이 나왔어요."를 보여 주는지 확인한다. */
    @Test fun blankMessageGetsADefault() {
        // message를 공백 한 칸(" ")으로 보냄 → 앱이 기본 문구로 바꿔 넣어야 함
        assertEquals("새 버전이 나왔어요.", parseAppUpdate(answer("optional", message = " "))!!.message)
    }
}
