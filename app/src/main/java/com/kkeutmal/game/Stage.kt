package com.kkeutmal.game

import kotlin.random.Random

data class Boss(val name: String, val rules: List<BossRule>)

data class StageConfig(
    val stage: Int,
    val timerSec: Int,
    val aiLevel: AiLevel,
    val targetRounds: Int,
    val allowHanbang: Boolean,
    /** 이름이 붙은 보스. 보스 스테이지가 아니면 null */
    val boss: Boss?,
    /** 이 스테이지에 실제로 걸리는 규칙 전부. 일반 스테이지도 하나씩 갖는다 */
    val rules: List<BossRule>
) {
    /** 화면에 보여줄 규칙 문구. 단어 제약이 없으면 빈 문자열 */
    val ruleLabel: String get() = rules.rejectionMessage()
}

/** 스테이지 번호만으로 난이도와 규칙을 계산한다. 수제 데이터 없음. */
object Stage {
    private const val BOSS_EVERY = 5

    /** 1스테이지는 규칙 없이 시작해 조작을 익히게 한다 */
    private const val FIRST_RULED_STAGE = 2

    // 단어에 거는 제약. 난이도 순으로 세 묶음.
    //
    // 사전 전수 측정(tools/RuleSafetySim.java) 결과 "이을 단어가 아예 없는 시작 글자"의
    // 단어량 비율이 전부 0.3% 미만인 규칙만 골랐다.
    // MIN_LEN_5(5글자 이상)는 시작 글자의 35%가 막히고 통과 단어 풀도 9만개뿐이라
    // 판이 자주 허무하게 끝나서 제외했다. 판정 로직은 남아 있으니 필요하면 되살릴 수 있다.
    private val EASY_RULES = listOf(
        BossRule.MIN_LEN_3,
        BossRule.EXACT_LEN_2,
        BossRule.ENDS_WITH_JONGSEONG,
        BossRule.NO_JONGSEONG
    )
    private val MID_RULES = listOf(
        BossRule.EXACT_LEN_3,
        BossRule.MIN_LEN_4
    )
    private val HARD_RULES = listOf(
        BossRule.EXACT_LEN_4
    )

    /** 단어와 무관한 압박 규칙. 보스에서만 단어 제약 위에 하나 더 얹는다 */
    private val MODIFIERS = listOf(BossRule.TIME_8, BossRule.AI_HANBANG)

    fun isBossStage(n: Int): Boolean = n > 0 && n % BOSS_EVERY == 0

    fun stagesToNextBoss(n: Int): Int = if (n <= 0) BOSS_EVERY else (BOSS_EVERY - n % BOSS_EVERY) % BOSS_EVERY

    fun configFor(n: Int): StageConfig {
        val boss = bossFor(n)
        val rules = boss?.rules ?: normalRules(n)
        val baseTimer = maxOf(8, 30 - n / 3)
        return StageConfig(
            stage = n,
            timerSec = if (BossRule.TIME_8 in rules) 8 else baseTimer,
            aiLevel = aiLevelFor(n),
            targetRounds = 3 + n / 3,
            allowHanbang = n >= 31,
            boss = boss,
            rules = rules
        )
    }

    private fun aiLevelFor(n: Int): AiLevel = when {
        n <= 5 -> AiLevel.VERY_EASY
        n <= 15 -> AiLevel.EASY
        n <= 30 -> AiLevel.NORMAL
        else -> AiLevel.HARD
    }

    /** 스테이지가 오를수록 더 까다로운 제약이 나오게 후보를 넓힌다 */
    private fun wordRulePool(n: Int): List<BossRule> = when {
        n <= 9 -> EASY_RULES
        n <= 19 -> EASY_RULES + MID_RULES
        else -> MID_RULES + HARD_RULES
    }

    /**
     * 일반 스테이지도 단어 제약을 하나씩 갖는다.
     *
     * 스테이지마다 독립적으로 무작위를 뽑으면 규칙 종류가 3~6개뿐이라
     * "3글자 이상"이 세 판 연속 나오는 일이 흔하다. 그래서 풀 순서를 한 번 고정해두고
     * 스테이지 번호로 그 안을 순서대로 돈다 — 같은 티어 안에서는 인접한 스테이지가
     * 절대 같은 규칙을 갖지 않고, 같은 스테이지는 언제나 같은 규칙이 나온다.
     */
    private fun normalRules(n: Int): List<BossRule> {
        if (n < FIRST_RULED_STAGE) return emptyList()
        val pool = wordRulePool(n)
        val order = pool.shuffled(Random(pool.size * 7919L))
        return listOf(order[n % order.size])
    }

    private fun bossFor(n: Int): Boss? {
        if (!isBossStage(n)) return null
        return when (n) {
            5 -> Boss("세글자 도깨비", listOf(BossRule.MIN_LEN_3))
            10 -> Boss("받침 지킴이", listOf(BossRule.ENDS_WITH_JONGSEONG))
            15 -> Boss("시간 도둑", listOf(BossRule.TIME_8))
            20 -> Boss("한방 마왕", listOf(BossRule.AI_HANBANG))
            25 -> Boss("긴말 여왕", listOf(BossRule.MIN_LEN_4))
            else -> Boss(mixedBossName(n), mixedRules(n))
        }
    }

    /**
     * 30스테이지 이상 보스: 단어 제약 1개 + 압박 규칙 1개.
     * 단어 제약끼리 섞으면 "두 글자만 + 3글자 이상"처럼 아무 단어도 통과 못 하는
     * 조합이 나올 수 있어서, 반드시 서로 충돌하지 않는 두 종류에서 하나씩 뽑는다.
     * 스테이지 번호를 시드로 써서 같은 스테이지는 항상 같은 규칙이 나온다.
     */
    private fun mixedRules(n: Int): List<BossRule> {
        val rng = Random(n.toLong())
        val word = wordRulePool(n).shuffled(rng).first()
        val modifier = MODIFIERS.shuffled(rng).first()
        return listOf(word, modifier)
    }

    private val MIXED_BOSS_NAMES = listOf(
        "혼돈의 문지기", "말꼬리 사냥꾼", "낱말 파수꾼", "글자 연금술사", "받침 대장군"
    )

    private fun mixedBossName(n: Int): String =
        MIXED_BOSS_NAMES[(n / BOSS_EVERY) % MIXED_BOSS_NAMES.size]
}
