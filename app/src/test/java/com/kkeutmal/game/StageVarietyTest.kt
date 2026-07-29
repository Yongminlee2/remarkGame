package com.kkeutmal.game

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 스테이지 조건이 한쪽으로 쏠리지 않는지 본다.
 *
 * "네 글자 이상 / 네 글자만이 너무 자주 나온다"는 지적에서 나온 테스트다.
 * 규칙 하나하나는 멀쩡해도, 높은 스테이지의 후보 묶음이 좁으면
 * 실제로 플레이하는 구간에서 같은 계열만 계속 만나게 된다.
 */
class StageVarietyTest {

    /** '네 글자' 계열 — 글자 수를 넷으로 못 박거나 넷 이상을 요구하는 규칙 */
    private val fourLetterRules = setOf(BossRule.EXACT_LEN_4, BossRule.MIN_LEN_4)

    private fun shareOfFourLetter(range: IntRange): Pair<Int, Int> {
        var four = 0
        var total = 0
        for (n in range) {
            val cfg = Stage.configFor(n)
            if (cfg.rules.isEmpty()) continue      // 이어가기 변경만 걸린 판
            total++
            if (cfg.rules.any { it in fourLetterRules }) four++
        }
        return four to total
    }

    @Test
    fun `네 글자 계열이 단어 제약의 절반을 넘지 않는다`() {
        // 실제로 오래 머무는 구간이 문제였다. 20스테이지 이상을 따로 본다.
        val (four, total) = shareOfFourLetter(20..200)
        val pct = 100.0 * four / total
        assertTrue(
            "20~200 스테이지에서 '네 글자' 계열이 %d/%d (%.0f%%) — 너무 잦다"
                .format(four, total, pct),
            pct <= 40.0
        )
    }

    @Test
    fun `전체 구간에서도 네 글자 계열이 한쪽으로 쏠리지 않는다`() {
        val (four, total) = shareOfFourLetter(2..200)
        val pct = 100.0 * four / total
        assertTrue(
            "2~200 스테이지에서 '네 글자' 계열이 %d/%d (%.0f%%)".format(four, total, pct),
            pct <= 35.0
        )
    }

    @Test
    fun `높은 스테이지에서도 규칙 종류가 넉넉하다`() {
        // 후보가 좁으면 무슨 수를 써도 같은 규칙이 자주 돌아온다.
        val kinds = (20..200).map { Stage.configFor(it) }
            .filter { it.rules.isNotEmpty() }
            .flatMap { it.rules }
            .filter { it.wordConstraint }
            .toSet()
        assertTrue("20스테이지 이상 단어 제약이 ${kinds.size}종뿐", kinds.size >= 6)
    }
}
