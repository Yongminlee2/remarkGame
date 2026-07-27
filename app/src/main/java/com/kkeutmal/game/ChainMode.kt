package com.kkeutmal.game

/**
 * 단어를 **어떻게 이어가는지** 정하는 방식.
 *
 * BossRule 이 "어떤 단어를 낼 수 있는가"(길이·받침 등)를 제한한다면,
 * ChainMode 는 "앞 단어와 어디를 맞춰야 하는가"를 바꾼다. 둘은 축이 다르다.
 */
enum class ChainMode(val label: String) {

    /** 기본 끝말잇기. 앞 단어의 끝 글자로 시작한다. 사과 → 과일 */
    TAIL(""),

    /** 앞 단어와 같은 글자로 계속 시작한다. 사과 → 사람 → 사탕 */
    SAME_HEAD("같은 글자로 계속 시작"),

    /** 앞말잇기. 앞 단어의 첫 글자로 **끝나는** 단어를 낸다. 사과 → 감사 → 영감 */
    HEAD("앞 글자로 끝내기 (거꾸로)");

    /**
     * 앞 단어에서 이어야 할 음절을 뽑는다.
     * 두음법칙은 어느 방식에서나 똑같이 인정한다(력→역).
     */
    fun linkSyllables(previous: String): Set<Char> = when (this) {
        TAIL -> Dueum.variants(previous.last())
        SAME_HEAD -> Dueum.variants(previous.first())
        HEAD -> Dueum.variants(previous.first())
    }

    /** 후보 단어에서 앞 단어와 맞춰야 하는 자리의 글자 */
    fun anchorOf(candidate: String): Char = when (this) {
        TAIL, SAME_HEAD -> candidate.first()
        HEAD -> candidate.last()
    }

    /** 사용자에게 보여줄 안내 문구 */
    fun promptFor(syllables: Set<Char>): String {
        val list = syllables.toList()
        val head = if (list.size == 1) "'${list[0]}'"
        else "'${list[0]}' (또는 '${list.drop(1).joinToString("', '")}')"
        return when (this) {
            TAIL -> "$head (으)로 시작"
            SAME_HEAD -> "$head (으)로 시작 — 계속 같은 글자!"
            HEAD -> "$head (으)로 끝나는 단어! (거꾸로)"
        }
    }
}
