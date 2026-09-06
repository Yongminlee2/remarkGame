package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 연승 보너스 코인.
 *
 * 코인을 주는 계산이라 틀리면 경제가 흔들린다. 특히 **상한이 빠지면** 한 번 잘 타는
 * 사람에게 코인이 무한정 나가는데, 그건 아무도 버그로 신고하지 않아서 알아채기 어렵다.
 */
class StreakBonusTest {

    @Test
    fun `한두 판으로는 연승 보너스가 없다`() {
        assertEquals(0, Progress.streakBonus(0))
        assertEquals(0, Progress.streakBonus(1))
    }

    @Test
    fun `2연승부터 붙고 연승이 길수록 늘어난다`() {
        assertEquals(10, Progress.streakBonus(2))
        assertEquals(15, Progress.streakBonus(3))
        assertTrue(Progress.streakBonus(5) > Progress.streakBonus(4))
    }

    @Test
    fun `10연승에서 멈춘다 — 상한이 없으면 코인이 무한정 나간다`() {
        assertEquals(50, Progress.streakBonus(10))
        assertEquals(50, Progress.streakBonus(11))
        assertEquals(50, Progress.streakBonus(999))
    }

    @Test
    fun `음수가 들어와도 코인을 뺏지 않는다`() {
        assertEquals(0, Progress.streakBonus(-3))
    }
}
