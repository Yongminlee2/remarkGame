package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossRuleTest {

    @Test
    fun `세 글자 이상 규칙`() {
        assertFalse(BossRule.MIN_LEN_3.accepts("사과"))
        assertTrue(BossRule.MIN_LEN_3.accepts("자동차"))
        assertTrue(BossRule.MIN_LEN_3.accepts("고등학교"))
    }

    @Test
    fun `네 글자 이상 규칙`() {
        assertFalse(BossRule.MIN_LEN_4.accepts("자동차"))
        assertTrue(BossRule.MIN_LEN_4.accepts("고등학교"))
    }

    @Test
    fun `받침으로 끝나는 단어 규칙`() {
        assertFalse(BossRule.ENDS_WITH_JONGSEONG.accepts("사과"))   // 과: 받침 없음
        assertTrue(BossRule.ENDS_WITH_JONGSEONG.accepts("사람"))    // 람: 받침 ㅁ
        assertTrue(BossRule.ENDS_WITH_JONGSEONG.accepts("책상"))    // 상: 받침 ㅇ
    }

    @Test
    fun `단어와 무관한 규칙은 항상 통과`() {
        assertTrue(BossRule.TIME_8.accepts("사과"))
        assertTrue(BossRule.AI_HANBANG.accepts("사과"))
    }

    @Test
    fun `규칙 목록은 모두 만족해야 통과`() {
        val rules = listOf(BossRule.MIN_LEN_3, BossRule.ENDS_WITH_JONGSEONG)
        assertFalse(rules.acceptsWord("사람"))       // 2글자라 탈락
        assertFalse(rules.acceptsWord("자동차"))     // 차: 받침 없음
        assertTrue(rules.acceptsWord("고등학생"))    // 4글자 + 생: 받침 ㅇ
    }

    @Test
    fun `빈 규칙 목록은 모두 통과`() {
        assertTrue(emptyList<BossRule>().acceptsWord("사과"))
    }

    @Test
    fun `안내 문구는 단어 제약만 모아 보여준다`() {
        assertEquals("3글자 이상", listOf(BossRule.MIN_LEN_3).rejectionMessage())
        assertEquals(
            "3글자 이상 · 받침으로 끝나는 단어",
            listOf(BossRule.MIN_LEN_3, BossRule.ENDS_WITH_JONGSEONG).rejectionMessage()
        )
        assertEquals("", listOf(BossRule.TIME_8).rejectionMessage())
    }
}
