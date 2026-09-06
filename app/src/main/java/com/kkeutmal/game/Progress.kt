package com.kkeutmal.game

import kotlin.math.pow

enum class Rank(val label: String, val minLevel: Int) {
    BRONZE("브론즈", 1),
    SILVER("실버", 10),
    GOLD("골드", 20),
    PLATINUM("플래티넘", 35),
    DIAMOND("다이아", 50),
    MASTER("마스터", 70),
    GRANDMASTER("그랜드마스터", 90)
}

/** XP·레벨·랭크 계산. 저장소를 모르는 순수 로직. */
object Progress {
    const val MAX_LEVEL = 99

    /** level 에서 level+1 로 가는 데 필요한 XP */
    fun xpForNextLevel(level: Int): Int =
        (50.0 * level.toDouble().pow(1.2)).toInt()

    /** level 에 도달하기까지 필요한 누적 XP */
    fun totalXpForLevel(level: Int): Int {
        var sum = 0
        for (l in 1 until level) sum += xpForNextLevel(l)
        return sum
    }

    fun levelForTotalXp(totalXp: Int): Int {
        var level = 1
        var need = xpForNextLevel(1)
        var remain = totalXp
        while (level < MAX_LEVEL && remain >= need) {
            remain -= need
            level++
            need = xpForNextLevel(level)
        }
        return level
    }

    fun xpIntoLevel(totalXp: Int): Int =
        totalXp - totalXpForLevel(levelForTotalXp(totalXp))

    fun rankOf(level: Int): Rank =
        Rank.entries.last { level >= it.minLevel }

    fun freeMatchXp(score: Int): Int = score / 5

    /**
     * 연승 보너스 코인. 이긴 판에만 얹어 준다.
     *
     * 첫 승에는 주지 않는다 — 한 판 이겼다고 "연승" 이라고 하면 말이 안 되고,
     * 이기면 늘 추가 코인이 나오는 셈이라 보너스가 보너스처럼 느껴지지 않는다.
     * 2연승부터 늘고 **10연승에서 멈춘다.** 상한이 없으면 한 번 잘 타는 사람이
     * 코인 경제를 무너뜨린다.
     *
     * **난이도로 배율을 준다.** 안 그러면 매우쉬움만 골라 연승을 쌓는 것이 가장
     * 이득이 되어, 어려운 판에 도전할 이유가 사라진다. 모험 모드도 스테이지가 정한
     * 난이도를 그대로 넘겨받으므로, 쉬운 초반 스테이지를 반복해 긁는 것도 같이 막힌다.
     *
     * @param level 그 판의 AI 난이도
     */
    fun streakBonus(streak: Int, level: AiLevel): Int {
        if (streak < 2) return 0
        val base = minOf(streak, 10) * 5
        return when (level) {
            AiLevel.VERY_EASY -> base * 2 / 5   // 10연승에 20
            AiLevel.EASY -> base * 7 / 10       // 10연승에 35
            AiLevel.NORMAL -> base              // 10연승에 50
            AiLevel.HARD -> base * 3 / 2        // 10연승에 75
        }
    }

    fun stageXp(stage: Int, isBoss: Boolean): Int {
        val base = stage * 10 + 50
        return if (isBoss) base * 3 else base
    }
}
