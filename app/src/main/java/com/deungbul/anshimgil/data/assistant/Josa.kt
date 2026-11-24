package com.deungbul.anshimgil.data.assistant

/*
 * Korean particles that follow how a word is read out, not only Hangul: a place name may end in a number or a
 * Latin letter ("국립아시아문화전당 GATE3" is read 게이트삼, so it takes 으로; "CU" is 씨유, so it takes 로).
 * Found in the usability check, round 2 (11/9): the app said "GATE3로".
 */

/** How a word's last sound ends: no final consonant, ㄹ (which takes 로 like a vowel), or another final consonant. */
enum class EndSound { VOWEL, RIEUL, CONSONANT }

/** Last digit as read in Korean (일 이 삼 …), and round numbers by their last unit (십, 백, 천, 만). */
private val DIGIT_SOUND = mapOf(
    '0' to EndSound.CONSONANT, '1' to EndSound.RIEUL, '2' to EndSound.VOWEL, '3' to EndSound.CONSONANT,
    '4' to EndSound.VOWEL, '5' to EndSound.VOWEL, '6' to EndSound.CONSONANT, '7' to EndSound.RIEUL,
    '8' to EndSound.RIEUL, '9' to EndSound.VOWEL,
)

/** Latin letters as read in Korean: 엘, 엠, 엔, 알 end in a consonant; the rest (에이, 비, 씨, 유 …) do not. */
private val LETTER_SOUND = mapOf('L' to EndSound.RIEUL, 'R' to EndSound.RIEUL, 'M' to EndSound.CONSONANT, 'N' to EndSound.CONSONANT)

fun endSound(word: String): EndSound {
    val w = word.trimEnd { it.isWhitespace() || it in ")]}.,!?" }
    val last = w.lastOrNull() ?: return EndSound.VOWEL
    val code = last.code - 0xAC00
    return when {
        code in 0..11171 -> when (code % 28) {
            0 -> EndSound.VOWEL
            8 -> EndSound.RIEUL
            else -> EndSound.CONSONANT
        }
        last.isDigit() -> {
            val digits = w.takeLastWhile { it.isDigit() }.trimStart('0')
            val zeros = digits.length - digits.trimEnd('0').length
            when {
                digits.isEmpty() -> EndSound.CONSONANT // 영
                zeros == 0 -> DIGIT_SOUND.getValue(last)
                zeros == 1 -> EndSound.CONSONANT // 십
                zeros == 2 -> EndSound.CONSONANT // 백
                zeros == 3 -> EndSound.CONSONANT // 천
                else -> EndSound.CONSONANT // 만, 십만 …
            }
        }
        last in 'a'..'z' || last in 'A'..'Z' -> LETTER_SOUND[last.uppercaseChar()] ?: EndSound.VOWEL
        else -> EndSound.VOWEL
    }
}

/** "집으로", "학교로", "GATE3으로", "CU로": the direction particle (ㄹ takes 로). */
fun withRo(word: String): String = word + if (endSound(word) == EndSound.CONSONANT) "으로" else "로"

/** "집이", "학교가", "GATE3이". */
fun withIGa(word: String): String = word + if (endSound(word) == EndSound.VOWEL) "가" else "이"

/** "집을", "학교를". */
fun withEulReul(word: String): String = word + if (endSound(word) == EndSound.VOWEL) "를" else "을"

/** "집은", "학교는". */
fun withEunNeun(word: String): String = word + if (endSound(word) == EndSound.VOWEL) "는" else "은"

/** "집이에요", "학교예요", "GATE3이에요". */
fun withIeyo(word: String): String = word + if (endSound(word) == EndSound.VOWEL) "예요" else "이에요"
