package com.kkeutmal.game

import kotlin.random.Random

data class Boss(val name: String, val rules: List<BossRule>)

data class StageConfig(
    val stage: Int,
    val timerSec: Int,
    val aiLevel: AiLevel,
    val targetRounds: Int,
    val allowHanbang: Boolean,
    val boss: Boss?
)

/** 스테이지 번호만으로 난이도를 계산한다. 수제 데이터 없음. */
object Stage {
    private const val BOSS_EVERY = 5

    private val MIXED_POOL = listOf(
        BossRule.MIN_LEN_3,
        BossRule.ENDS_WITH_JONGSEONG,
        BossRule.TIME_8,
        BossRule.AI_HANBANG,
        BossRule.MIN_LEN_4
    )

    fun isBossStage(n: Int): Boolean = n > 0 && n % BOSS_EVERY == 0

    fun stagesToNextBoss(n: Int): Int = if (n <= 0) BOSS_EVERY else (BOSS_EVERY - n % BOSS_EVERY) % BOSS_EVERY

    fun configFor(n: Int): StageConfig {
        val boss = bossFor(n)
        val baseTimer = maxOf(8, 30 - n / 3)
        val timer = if (boss != null && BossRule.TIME_8 in boss.rules) 8 else baseTimer
        return StageConfig(
            stage = n,
            timerSec = timer,
            aiLevel = aiLevelFor(n),
            targetRounds = 3 + n / 3,
            allowHanbang = n >= 31,
            boss = boss
        )
    }

    private fun aiLevelFor(n: Int): AiLevel = when {
        n <= 5 -> AiLevel.VERY_EASY
        n <= 15 -> AiLevel.EASY
        n <= 30 -> AiLevel.NORMAL
        else -> AiLevel.HARD
    }

    private fun bossFor(n: Int): Boss? {
        if (!isBossStage(n)) return null
        return when (n) {
            5 -> Boss("세글자 도깨비", listOf(BossRule.MIN_LEN_3))
            10 -> Boss("받침 지킴이", listOf(BossRule.ENDS_WITH_JONGSEONG))
            15 -> Boss("시간 도둑", listOf(BossRule.TIME_8))
            20 -> Boss("한방 마왕", listOf(BossRule.AI_HANBANG))
            25 -> Boss("긴말 여왕", listOf(BossRule.MIN_LEN_4))
            else -> Boss("혼돈의 문지기", mixedRules(n))
        }
    }

    /** 스테이지 번호를 시드로 써서 같은 스테이지는 항상 같은 규칙이 나오게 한다. */
    private fun mixedRules(n: Int): List<BossRule> =
        MIXED_POOL.shuffled(Random(n.toLong())).take(2)
}
