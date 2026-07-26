package com.kkeutmal.game

/**
 * 두음법칙: 앞 단어의 끝 글자를 다음 단어의 첫 글자로 이을 때 허용되는 변형.
 * 력→역, 라→나, 뇨→요 처럼 ㄹ/ㄴ 초성이 완화되는 표준 규칙(단방향)을 적용한다.
 */
object Dueum {
    private const val BASE = 0xAC00
    private const val LAST = 0xD7A3

    // 중성 인덱스: ㅑ(2) ㅒ(3) ㅕ(6) ㅖ(7) ㅛ(12) ㅠ(17) ㅣ(20) — i/y 계열
    private val IY_MEDIALS = setOf(2, 3, 6, 7, 12, 17, 20)

    private const val INITIAL_N = 2   // ㄴ
    private const val INITIAL_R = 5   // ㄹ
    private const val INITIAL_NG = 11 // ㅇ

    /** c 로 끝났을 때 다음 단어의 첫 글자로 허용되는 글자들(자기 자신 포함). */
    fun variants(c: Char): Set<Char> {
        val result = linkedSetOf(c)
        if (c.code < BASE || c.code > LAST) return result
        val code = c.code - BASE
        val initial = code / 588
        val medial = (code % 588) / 28
        val final = code % 28
        fun with(newInitial: Int) = (BASE + newInitial * 588 + medial * 28 + final).toChar()
        when (initial) {
            INITIAL_R -> result += if (medial in IY_MEDIALS) with(INITIAL_NG) else with(INITIAL_N)
            INITIAL_N -> if (medial in IY_MEDIALS) result += with(INITIAL_NG)
        }
        return result
    }

    fun isHangul(s: String): Boolean = s.isNotEmpty() && s.all { it.code in BASE..LAST }
}
