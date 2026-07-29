package com.kkeutmal.game

import kotlin.random.Random

data class Boss(
    val name: String,
    val rules: List<BossRule>,
    /** 이어가기 방식을 바꾸는 보스는 단어 제약을 겹쳐 걸지 않는다 — 둘 다 걸면 손쓸 수 없이 어려워진다 */
    val chainMode: ChainMode = ChainMode.TAIL
)

data class StageConfig(
    val stage: Int,
    val timerSec: Int,
    val aiLevel: AiLevel,
    val targetRounds: Int,
    val allowHanbang: Boolean,
    /** 이름이 붙은 보스. 보스 스테이지가 아니면 null */
    val boss: Boss?,
    /** 이 스테이지에 실제로 걸리는 단어 제약 전부. 일반 스테이지도 하나씩 갖는다 */
    val rules: List<BossRule>,
    /** 단어를 어떻게 이어가는지. 기본은 평범한 끝말잇기 */
    val chainMode: ChainMode = ChainMode.TAIL
) {
    /** 화면에 보여줄 조건 문구. 단어 제약과 이어가기 방식을 함께 담는다 */
    val ruleLabel: String
        get() {
            val parts = buildList {
                if (chainMode != ChainMode.TAIL) add(chainMode.label)
                rules.rejectionMessage().takeIf { it.isNotEmpty() }?.let { add(it) }
            }
            // 시간 도둑·한방 마왕처럼 단어 제약이 없는 보스도 무엇이 달라지는지는 알려줘야 한다
            if (parts.isEmpty() && rules.isNotEmpty()) {
                return rules.joinToString(" · ") { it.label }
            }
            return parts.joinToString(" · ")
        }
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

    /**
     * 스테이지에 걸리는 특수 조건 한 가지.
     * 단어 자체를 제한하거나(Word), 이어가는 방식을 바꾼다(Chain). 둘은 축이 달라 섞지 않는다.
     */
    private sealed class Mod {
        data class Word(val rule: BossRule) : Mod()
        data class Chain(val mode: ChainMode) : Mod()
    }

    /** 이어가기 방식 변경은 단어 제약보다 낯설어서 조금 뒤(6스테이지)부터 등장시킨다 */
    private const val FIRST_CHAIN_STAGE = 6

    fun isBossStage(n: Int): Boolean = n > 0 && n % BOSS_EVERY == 0

    fun stagesToNextBoss(n: Int): Int = if (n <= 0) BOSS_EVERY else (BOSS_EVERY - n % BOSS_EVERY) % BOSS_EVERY

    fun configFor(n: Int): StageConfig {
        val boss = bossFor(n)
        val mod = if (boss == null) normalMod(n) else null
        val rules = when {
            boss != null -> boss.rules
            mod is Mod.Word -> listOf(mod.rule)
            else -> emptyList()
        }
        val chain = boss?.chainMode ?: (mod as? Mod.Chain)?.mode ?: ChainMode.TAIL
        val baseTimer = maxOf(8, 30 - n / 3)
        return StageConfig(
            stage = n,
            timerSec = if (BossRule.TIME_8 in rules) 8 else baseTimer,
            aiLevel = aiLevelFor(n),
            targetRounds = 3 + n / 3,
            allowHanbang = n >= 31,
            boss = boss,
            rules = rules,
            chainMode = chain
        )
    }


    private fun aiLevelFor(n: Int): AiLevel = when {
        n <= 5 -> AiLevel.VERY_EASY
        n <= 15 -> AiLevel.EASY
        n <= 30 -> AiLevel.NORMAL
        else -> AiLevel.HARD
    }

    /**
     * 스테이지가 오를수록 후보를 **넓힌다**. 어려운 것만 남기지 않는다.
     *
     * 예전에는 20스테이지 이상에서 쉬운 규칙을 아예 빼고 [EXACT_LEN_3, MIN_LEN_4,
     * EXACT_LEN_4] 셋만 남겼다. 그런데 이 셋 중 둘이 '네 글자' 계열이라
     * 실제로 오래 머무는 구간에서 네 글자 조건만 계속 만나게 됐다.
     *
     * 난이도는 제한시간(30→8초)과 AI 레벨이 이미 올리고 있다.
     * 단어 제약은 판마다 다른 맛을 내는 쪽이 낫다.
     */
    private fun wordRulePool(n: Int): List<BossRule> = when {
        n <= 9 -> EASY_RULES
        n <= 19 -> EASY_RULES + MID_RULES
        else -> EASY_RULES + MID_RULES + HARD_RULES
    }

    /**
     * 일반 스테이지도 조건을 하나씩 갖는다.
     *
     * 스테이지마다 독립적으로 무작위를 뽑으면 후보가 몇 개 안 돼서 같은 규칙이
     * 연달아 나온다. 그래서 후보 순서를 한 번 고정해 두고 순서대로 돈다 —
     * 인접한 스테이지는 절대 같은 조건을 갖지 않고, 같은 스테이지는 언제나 같은 조건이 나온다.
     *
     * 세는 기준은 **스테이지 번호가 아니라 '보스가 아닌 스테이지의 순번'** 이다.
     * 번호를 그대로 쓰면 보스 주기(5)와 후보 개수가 맞아떨어질 때
     * 특정 후보가 보스 자리에만 배정돼 **한 번도 안 나오는** 일이 생긴다.
     * 실제로 후보가 5개이던 구간에서 규칙 하나가 통째로 사라져 있었다.
     */
    private fun normalMod(n: Int): Mod? {
        if (n < FIRST_RULED_STAGE) return null
        val pool = modPool(n)
        val order = pool.shuffled(Random(pool.size * 7919L))
        return order[nonBossOrdinal(n) % order.size]
    }

    /** n 이하의 '보스가 아닌 스테이지' 개수. 보스를 건너뛰어도 1씩 늘어난다. */
    private fun nonBossOrdinal(n: Int): Int = n - n / BOSS_EVERY

    /** 이 스테이지에서 나올 수 있는 특수 조건 후보. 오를수록 넓어진다 */
    private fun modPool(n: Int): List<Mod> {
        val words = wordRulePool(n).map { Mod.Word(it) }
        return if (n >= FIRST_CHAIN_STAGE) {
            words + Mod.Chain(ChainMode.SAME_HEAD) + Mod.Chain(ChainMode.HEAD)
        } else {
            words
        }
    }

    private fun bossFor(n: Int): Boss? {
        if (!isBossStage(n)) return null
        return when (n) {
            5 -> Boss("세글자 도깨비", listOf(BossRule.MIN_LEN_3))
            10 -> Boss("받침 지킴이", listOf(BossRule.ENDS_WITH_JONGSEONG))
            15 -> Boss("시간 도둑", listOf(BossRule.TIME_8))
            20 -> Boss("한방 마왕", listOf(BossRule.AI_HANBANG))
            25 -> Boss("긴말 여왕", listOf(BossRule.MIN_LEN_4))
            35 -> Boss("거꾸로 대왕", emptyList(), ChainMode.HEAD)
            40 -> Boss("외곬 고집쟁이", emptyList(), ChainMode.SAME_HEAD)
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
