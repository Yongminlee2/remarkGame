package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GameEngine 은 WordDict(안드로이드 assets)에 의존하므로 여기서는
 * 규칙 필터링 자체만 검증한다. 사전이 걸린 동작은 시뮬레이션과 실기기에서 본다.
 */
class GameEngineRuleTest {

    @Test
    fun `규칙이 없으면 모든 단어를 통과시킨다`() {
        val rules = emptyList<BossRule>()
        assertTrue(listOf("사과", "자동차", "고등학교").all { rules.acceptsWord(it) })
    }

    @Test
    fun `받침 규칙은 후보를 걸러낸다`() {
        val rules = listOf(BossRule.ENDS_WITH_JONGSEONG)
        val candidates = listOf("사과", "사람", "바다", "가방")
        assertEquals(listOf("사람", "가방"), candidates.filter { rules.acceptsWord(it) })
    }

    @Test
    fun `규칙을 통과하는 후보가 하나도 없을 수 있다`() {
        val rules = listOf(BossRule.MIN_LEN_4)
        val candidates = listOf("사과", "바다", "가방")
        assertTrue(candidates.none { rules.acceptsWord(it) })
    }

    @Test
    fun `안내 문구는 단어 제약만 담는다`() {
        assertEquals("받침으로 끝나는 단어", listOf(BossRule.ENDS_WITH_JONGSEONG).rejectionMessage())
        assertFalse(listOf(BossRule.AI_HANBANG).rejectionMessage().isNotEmpty())
    }
}
