package com.kkeutmal.game

enum class GameMode { FREE, ADVENTURE }

data class GameOutcome(
    val mode: GameMode,
    val won: Boolean,
    val score: Int,
    val rounds: Int,
    val stage: Int,
    val isBoss: Boolean,
    val doubleReward: Boolean
)

data class Reward(val coins: Int, val xp: Int)

/** 한 판이 끝났을 때 줄 코인·XP 계산. 저장은 호출하는 쪽이 한다. */
object GameResult {
    fun rewardFor(outcome: GameOutcome): Reward {
        val base = when (outcome.mode) {
            GameMode.FREE -> Reward(
                coins = outcome.score / 10 + if (outcome.won) 20 else 0,
                xp = Progress.freeMatchXp(outcome.score)
            )
            GameMode.ADVENTURE -> {
                if (!outcome.won) Reward(0, 0)
                else {
                    val coins = 10 + outcome.stage * 2
                    Reward(
                        coins = if (outcome.isBoss) coins * 3 else coins,
                        xp = Progress.stageXp(outcome.stage, outcome.isBoss)
                    )
                }
            }
        }
        return if (outcome.doubleReward) Reward(base.coins * 2, base.xp * 2) else base
    }
}
