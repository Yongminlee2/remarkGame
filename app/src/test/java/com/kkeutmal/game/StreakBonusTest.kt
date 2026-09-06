package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 연승 보너스 코인.
 *
 * 코인을 주는 계산이라 틀리면 경제가 흔들리는데, **틀려도 아무도 버그로 신고하지 않는다.**
 * 코인이 더 나오는 쪽으로 틀리면 오히려 좋아하고, 덜 나오는 쪽으로 틀려도 원래 그런 줄 안다.
 *
 * 지키려는 것 두 가지:
 * - **상한.** 없으면 한 번 잘 타는 사람에게 코인이 무한정 나간다.
 * - **난이도 배율.** 없으면 매우쉬움만 골라 연승을 쌓는 것이 가장 이득이 되어,
 *   어려운 판에 도전할 이유가 사라진다. 모험도 스테이지 난이도를 그대로 넘겨받으므로
 *   쉬운 초반 스테이지를 반복해 긁는 것이 같은 이유로 막힌다.
 */
class StreakBonusTest {

    @Test
    fun `한두 판으로는 연승 보너스가 없다`() {
        for (level in AiLevel.values()) {
            assertEquals("$level 0연승", 0, Progress.streakBonus(0, level))
            assertEquals("$level 1연승", 0, Progress.streakBonus(1, level))
        }
    }

    @Test
    fun `2연승부터 붙고 연승이 길수록 늘어난다`() {
        assertEquals(10, Progress.streakBonus(2, AiLevel.NORMAL))
        assertEquals(15, Progress.streakBonus(3, AiLevel.NORMAL))
        assertTrue(Progress.streakBonus(5, AiLevel.NORMAL) > Progress.streakBonus(4, AiLevel.NORMAL))
    }

    @Test
    fun `10연승에서 멈춘다 — 상한이 없으면 코인이 무한정 나간다`() {
        for (level in AiLevel.values()) {
            val cap = Progress.streakBonus(10, level)
            assertEquals("$level 11연승", cap, Progress.streakBonus(11, level))
            assertEquals("$level 999연승", cap, Progress.streakBonus(999, level))
        }
    }

    @Test
    fun `어려울수록 더 준다 — 쉬운 난이도로 긁는 것이 이득이면 안 된다`() {
        val s = 10
        val veryEasy = Progress.streakBonus(s, AiLevel.VERY_EASY)
        val easy = Progress.streakBonus(s, AiLevel.EASY)
        val normal = Progress.streakBonus(s, AiLevel.NORMAL)
        val hard = Progress.streakBonus(s, AiLevel.HARD)
        assertTrue("매우쉬움($veryEasy) < 쉬움($easy)", veryEasy < easy)
        assertTrue("쉬움($easy) < 보통($normal)", easy < normal)
        assertTrue("보통($normal) < 어려움($hard)", normal < hard)
    }

    @Test
    fun `쉬운 난이도라도 코인을 뺏지는 않는다`() {
        for (level in AiLevel.values()) {
            assertTrue("$level 이 음수를 준다", Progress.streakBonus(10, level) > 0)
            assertEquals("음수 연승", 0, Progress.streakBonus(-3, level))
        }
    }
}
