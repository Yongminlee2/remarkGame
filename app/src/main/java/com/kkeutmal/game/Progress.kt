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

    fun stageXp(stage: Int, isBoss: Boolean): Int {
        val base = stage * 10 + 50
        return if (isBoss) base * 3 else base
    }
}
