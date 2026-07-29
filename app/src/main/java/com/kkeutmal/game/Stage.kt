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
        BossRule.EXACT_LEN_3
    )

    // '네 글자' 계열 둘은 여기 모아 뒀다. 체감이 서로 비슷해서 자주 만나면
    // 같은 조건이 반복되는 것처럼 느껴진다는 지적이 있었다.
    // 20스테이지부터만 나오고, 나올 때도 다른 규칙의 절반 빈도로만 나온다.
    private val HARD_RULES = listOf(
        BossRule.MIN_LEN_4,
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
        val order = cycleOrder(modPool(n))
        return order[nonBossOrdinal(n) % order.size]
    }

    /** n 이하의 '보스가 아닌 스테이지' 개수. 보스를 건너뛰어도 1씩 늘어난다. */
    private fun nonBossOrdinal(n: Int): Int = n - n / BOSS_EVERY

    /**
     * '네 글자' 계열 둘. 체감이 서로 비슷해서 자주 만나면 같은 조건이 반복되는 것처럼 느껴진다.
     * 그래서 다른 규칙의 절반만 나오게 한다.
     */
    private val HALF_RATE_RULES = setOf(BossRule.EXACT_LEN_4, BossRule.MIN_LEN_4)

    /** 후보가 한 바퀴에 몇 번 들어가는지 */
    private fun weightOf(rule: BossRule) = if (rule in HALF_RATE_RULES) 1 else 2

    /**
     * 이 스테이지에서 나올 수 있는 특수 조건 후보.
     *
     * 후보를 한 번씩만 넣으면 전부 같은 빈도로 나온다. 빈도를 다르게 주려고
     * **자주 나올 후보를 두 번 넣는다.** 그러면 한 바퀴에서 차지하는 몫이 두 배가 된다.
     */
    private fun modPool(n: Int): List<Mod> {
        val out = ArrayList<Mod>()
        for (r in wordRulePool(n)) {
            repeat(weightOf(r)) { out.add(Mod.Word(r)) }
        }
        if (n >= FIRST_CHAIN_STAGE) {
            repeat(2) {
                out.add(Mod.Chain(ChainMode.SAME_HEAD))
                out.add(Mod.Chain(ChainMode.HEAD))
            }
        }
        return out
    }

    /**
     * 조건을 '계열'로 묶는다. 플레이어가 같은 것으로 느끼는 단위다.
     *
     * '네 글자 단어만'과 '4글자 이상'은 서로 다른 규칙이지만 체감이 거의 같다.
     * 둘이 붙어 나오면 같은 조건을 두 판 연속 만난 것처럼 느껴진다.
     * 그래서 순서를 짤 때 이 둘을 한 계열로 보고 떼어 놓는다.
     */
    private fun familyOf(mod: Mod): String = when (mod) {
        is Mod.Word -> if (mod.rule in HALF_RATE_RULES) "네글자" else mod.rule.name
        is Mod.Chain -> "이어가기:" + mod.mode.name
    }

    /**
     * 한 바퀴 도는 순서를 만든다.
     *
     * 같은 후보를 여러 번 넣었으므로 그냥 섞으면 둘이 나란히 붙을 수 있다.
     * 그래서 **같은 후보끼리는 서로 다른 바퀴에 하나씩** 넣는다 — 한 바퀴 안에는
     * 서로 다른 후보만 들어가므로 바퀴 안에서는 같은 후보가 붙을 일이 없다.
     *
     * 그러고도 '계열'이 같은 이웃은 생길 수 있어서(네 글자 계열 둘) 마지막에 한 번 훑어
     * 떼어 놓는다. 순서는 끝과 처음이 이어지는 고리라 마지막 자리도 함께 본다.
     */
    private fun cycleOrder(pool: List<Mod>): List<Mod> {
        val rounds = ArrayList<MutableList<Mod>>()
        for ((mod, copies) in pool.groupBy { it }) {
            for (i in copies.indices) {
                while (rounds.size <= i) rounds.add(ArrayList())
                rounds[i].add(mod)
            }
        }
        val out = rounds
            .mapIndexed { i, r -> r.shuffled(Random((i + 1) * 7919L)) }
            .flatten()
            .toMutableList()
        separateSameFamily(out)
        return out
    }

    /** 이웃한 자리의 계열이 같으면 뒤쪽 것과 자리를 바꿔 떼어 놓는다 */
    private fun separateSameFamily(order: MutableList<Mod>) {
        val n = order.size
        if (n < 4) return

        fun famAt(i: Int) = familyOf(order[((i % n) + n) % n])

        for (i in 0 until n) {
            if (famAt(i) != famAt(i + 1)) continue
            val moving = order[(i + 1) % n]
            // 옮겨 갈 자리를 찾는다. 서로 자리를 바꿔도 양쪽 다 이웃과 겹치지 않아야 한다.
            for (step in 2 until n) {
                val j = (i + 1 + step) % n
                val candidate = order[j]
                val okHere = familyOf(candidate) != famAt(i) && familyOf(candidate) != famAt(i + 2)
                val okThere = familyOf(moving) != famAt(j - 1) && familyOf(moving) != famAt(j + 1)
                if (okHere && okThere) {
                    order[(i + 1) % n] = candidate
                    order[j] = moving
                    break
                }
            }
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
        // 일반 스테이지와 같은 빈도를 쓴다. 보스만 '네 글자' 계열이 잦으면
        // 애써 낮춘 빈도가 다시 올라간다.
        val weighted = wordRulePool(n).flatMap { r -> List(weightOf(r)) { r } }
        val word = weighted.shuffled(rng).first()
        val modifier = MODIFIERS.shuffled(rng).first()
        return listOf(word, modifier)
    }

    private val MIXED_BOSS_NAMES = listOf(
        "혼돈의 문지기", "말꼬리 사냥꾼", "낱말 파수꾼", "글자 연금술사", "받침 대장군"
    )

    private fun mixedBossName(n: Int): String =
        MIXED_BOSS_NAMES[(n / BOSS_EVERY) % MIXED_BOSS_NAMES.size]
}
