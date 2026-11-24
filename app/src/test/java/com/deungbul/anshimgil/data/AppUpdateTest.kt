package com.deungbul.anshimgil.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AppUpdateTest {
    private fun answer(update: String, url: String = "https://example.com/seipjido.apk", message: String = "새 버전이 나왔어요…") =
        JSONObject(
            """{"latestVersionCode":5,"latestVersionName":"1.4","minVersionCode":4,"message":"$message","downloadUrl":"$url","update":"$update"}""",
        )

    @Test fun requiredCannotBeClosed() {
        val u = parseAppUpdate(answer("required"))!!
        assertTrue(u.required)
        assertEquals("새 버전이 나왔어요…", u.message)
        assertEquals("https://example.com/seipjido.apk", u.downloadUrl)
        assertEquals("1.4", u.versionName)
    }

    @Test fun optionalCanWait() {
        assertFalse(parseAppUpdate(answer("optional"))!!.required)
    }

    @Test fun noneShowsNothing() {
        assertNull(parseAppUpdate(answer("none")))
    }

    /**
     * 역할: 비었거나 약속에 없는 값, 주소가 없거나 http(s)가 아닌 주소가 오면 업데이트 창을 띄우지 않는지 확인한다.
     * 닫을 수 없는 '꼭 업데이트' 창에 갇히거나, 버튼이 엉뚱한 앱을 여는 것을 막는다.
     */
    @Test fun oddAnswersShowNothing() {
        assertNull(parseAppUpdate(JSONObject()))
        assertNull(parseAppUpdate(answer("REQUIRED")))
        assertNull(parseAppUpdate(answer("required", url = "")))
        assertNull(parseAppUpdate(answer("required", url = "intent://evil")))
    }

    @Test fun blankMessageGetsADefault() {
        assertEquals("새 버전이 나왔어요.", parseAppUpdate(answer("optional", message = " "))!!.message)
    }
}
