package com.kkeutmal.game

/** 도전과제 판정에 쓰는 누적 기록 묶음. 저장소를 모르는 값 객체. */
data class PlayerStats(
    val bestRounds: Int,
    val bestStage: Int,
    val ownedAvatarCount: Int,
    val bestStreak: Int
)

enum class Achievement(val id: String, val label: String) {
    ROUNDS_20("ach_rounds_20", "한 판에서 20라운드 버티기"),
    STAGE_50("ach_stage_50", "50스테이지 도달"),
    COLLECT_30("ach_collect_30", "아바타 30종 수집"),
    STREAK_7("ach_streak_7", "7일 연속 출석");

    fun isMet(stats: PlayerStats): Boolean = when (this) {
        ROUNDS_20 -> stats.bestRounds >= 20
        STAGE_50 -> stats.bestStage >= 50
        COLLECT_30 -> stats.ownedAvatarCount >= 30
        STREAK_7 -> stats.bestStreak >= 7
    }
}

object Achievements {
    fun labelOf(id: String): String =
        Achievement.entries.firstOrNull { it.id == id }?.label ?: id

    fun metBy(stats: PlayerStats): List<Achievement> =
        Achievement.entries.filter { it.isMet(stats) }
}
