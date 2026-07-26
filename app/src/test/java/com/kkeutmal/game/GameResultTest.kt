package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameResultTest {

    private fun free(score: Int, won: Boolean) = GameOutcome(
        mode = GameMode.FREE, won = won, score = score, rounds = 5,
        stage = 0, isBoss = false, doubleReward = false
    )

    private fun adventure(stage: Int, won: Boolean, isBoss: Boolean = false, doubleReward: Boolean = false) =
        GameOutcome(
            mode = GameMode.ADVENTURE, won = won, score = 0, rounds = 5,
            stage = stage, isBoss = isBoss, doubleReward = doubleReward
        )

    @Test
    fun `자유 대전 보상은 점수 기반이고 승리 보너스가 붙는다`() {
        assertEquals(Reward(coins = 30, xp = 60), GameResult.rewardFor(free(300, won = false)))
        assertEquals(Reward(coins = 50, xp = 60), GameResult.rewardFor(free(300, won = true)))
    }

    @Test
    fun `모험은 클리어했을 때만 보상을 준다`() {
        assertEquals(Reward(coins = 0, xp = 0), GameResult.rewardFor(adventure(10, won = false)))
        assertEquals(Reward(coins = 30, xp = 150), GameResult.rewardFor(adventure(10, won = true)))
    }

    @Test
    fun `보스 클리어는 코인과 XP가 3배다`() {
        assertEquals(Reward(coins = 90, xp = 450), GameResult.rewardFor(adventure(10, won = true, isBoss = true)))
    }

    @Test
    fun `2배 획득 아이템은 코인과 XP를 모두 2배로 만든다`() {
        assertEquals(
            Reward(coins = 60, xp = 300),
            GameResult.rewardFor(adventure(10, won = true, doubleReward = true))
        )
    }

    @Test
    fun `모험 실패는 2배 아이템을 써도 0이다`() {
        assertEquals(
            Reward(coins = 0, xp = 0),
            GameResult.rewardFor(adventure(10, won = false, doubleReward = true))
        )
    }
}
