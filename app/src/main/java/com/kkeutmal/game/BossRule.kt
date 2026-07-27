package com.kkeutmal.game

/**
 * 스테이지에 걸리는 특수 규칙.
 * accepts 가 단어에 거는 제약이고, TIME_8·AI_HANBANG 처럼 단어와 무관한 규칙은 항상 true 를 준다.
 */
enum class BossRule(val label: String, val wordConstraint: Boolean) {
    EXACT_LEN_2("두 글자 단어만", true),
    EXACT_LEN_3("세 글자 단어만", true),
    EXACT_LEN_4("네 글자 단어만", true),
    MIN_LEN_3("3글자 이상", true),
    MIN_LEN_4("4글자 이상", true),
    MIN_LEN_5("5글자 이상", true),
    ENDS_WITH_JONGSEONG("받침으로 끝나는 단어", true),
    NO_JONGSEONG("받침 없이 끝나는 단어", true),
    TIME_8("제한시간 8초", false),
    AI_HANBANG("AI가 한방단어를 노림", false);

    fun accepts(word: String): Boolean {
        if (word.isEmpty()) return false
        return when (this) {
            EXACT_LEN_2 -> word.length == 2
            EXACT_LEN_3 -> word.length == 3
            EXACT_LEN_4 -> word.length == 4
            MIN_LEN_3 -> word.length >= 3
            MIN_LEN_4 -> word.length >= 4
            MIN_LEN_5 -> word.length >= 5
            ENDS_WITH_JONGSEONG -> hasJongseong(word.last())
            NO_JONGSEONG -> !hasJongseong(word.last())
            TIME_8, AI_HANBANG -> true
        }
    }

    private fun hasJongseong(c: Char): Boolean {
        val code = c.code - 0xAC00
        if (code < 0 || code > 11171) return false
        return code % 28 != 0
    }
}

fun List<BossRule>.acceptsWord(word: String): Boolean =
    word.isNotEmpty() && all { it.accepts(word) }

fun List<BossRule>.rejectionMessage(): String =
    filter { it.wordConstraint }.joinToString(" · ") { it.label }
