package com.deungbul.anshimgil.data.assistant

// 시험에 쓰는 도구들
import org.junit.Assert.assertEquals    // assertEquals(기대값, 실제값): 둘이 같아야 통과
import org.junit.Test                   // @Test가 붙은 함수 하나하나가 시험 하나

/**
 * 음성 안내가 긴급 전화번호를 읽는 방식 시험.
 *
 * 폰의 음성 읽기(TTS)는 "112"를 숫자로 보고 "백십이"라고 읽는다. 급한 사람에게는 "일일이"처럼
 * 한 자씩 읽어 주는 편이 알아듣기 쉽다. 그래서 앱은 소리 내어 읽기 직전에 글을 spokenNumbers()에
 * 넣어 112는 "일일이", 119는 "일일구"로 바꾼다. 화면에 보이는 글은 그대로 112다.
 *
 * 여기서는 진짜로 소리를 내지 않는다. 글을 spokenNumbers()에 넣고, 돌려받은 글(바꾼 뒤의 글)이
 * 기대한 글과 같은지만 본다. spokenNumbers()는 앞뒤에 다른 숫자가 붙지 않은 112·119만 바꾸고,
 * 나머지 글자는 그대로 둔 새 글을 돌려준다.
 */
class SpokenNumbersTest {
    /**
     * 역할: 음성 안내가 112·119를 "백십이"가 아니라 "일일이"처럼 한 자씩 읽는지 확인한다.
     * 1120m 같은 다른 숫자는 바꾸지 않아야 한다.
     */
    @Test
    fun emergencyNumbersAreReadDigitByDigit() {
        // EMERGENCY_SAY는 앱이 급한 말을 들었을 때 하는 안내 문장이다(앱 코드에 정해진 상수).
        // 원문: "괜찮으세요? 위험하면 바로 112에 전화하세요. 112에 전화할까요, …"
        // 이 문장을 읽기용으로 바꾸면 112 두 군데가 모두 "일일이"가 되어야 한다.
        assertEquals(
            "괜찮으세요? 위험하면 바로 일일이에 전화하세요. 일일이에 전화할까요, 보호자에게 알릴까요, 가까운 파출소로 안내할까요?", // 기대하는 읽기용 문장
            spokenNumbers(EMERGENCY_SAY),                                                                                        // 실제로 바꾼 문장
        )
        // 사용자가 한 말 "119 불러 줘"를 읽을 때도 119가 "일일구"로 바뀌어야 한다.
        assertEquals("일일구 불러 줘", spokenNumbers("119 불러 줘"))
        // "1120m"의 112 뒤에는 숫자 0이 붙어 있으므로 긴급 번호가 아니다. 글이 하나도 바뀌지 않아야 한다.
        assertEquals("1120m 앞", spokenNumbers("1120m 앞")) // other numbers stay
    }
}
