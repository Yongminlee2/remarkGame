package com.kkeutmal.game

import java.time.LocalDate
import kotlin.random.Random

enum class Aggregate { SUM, MAX }

enum class Mission(
    val id: String,
    val label: String,
    val target: Int,
    val reward: Int,
    val aggregate: Aggregate
) {
    PLAY_3("m_play3", "게임 3판 하기", 3, 30, Aggregate.SUM),
    ROUNDS_5("m_rounds5", "5라운드 이상 버티기", 5, 40, Aggregate.MAX),
    LONG_WORD_3("m_long3", "4글자 이상 단어 3번 쓰기", 3, 40, Aggregate.SUM),
    STAGE_2("m_stage2", "모험 스테이지 2개 클리어", 2, 50, Aggregate.SUM),
    SURRENDER_1("m_surrender1", "AI 항복시키기", 1, 50, Aggregate.SUM),
    VOICE_5("m_voice5", "음성으로 단어 5번 내기", 5, 40, Aggregate.SUM),
    SCORE_300("m_score300", "누적 300점 얻기", 300, 30, Aggregate.SUM)
}

data class StreakResult(val days: Int, val reward: Int, val isNewDay: Boolean)

/** 일일 미션과 연속 출석. 날짜는 yyyy-MM-dd 문자열로 주고받아 테스트 가능하게 한다. */
object Missions {
    const val ALL_CLEAR_BONUS = 50

    private val STREAK_REWARDS = listOf(0, 20, 30, 40, 50, 60, 100) // 1~7일차

    fun pickDaily(dateKey: String): List<Mission> =
        Mission.entries.shuffled(Random(dateKey.hashCode().toLong())).take(3)

    fun applyProgress(mission: Mission, current: Int, amount: Int): Int =
        when (mission.aggregate) {
            Aggregate.SUM -> current + amount
            Aggregate.MAX -> maxOf(current, amount)
        }

    fun isComplete(mission: Mission, progress: Int): Boolean = progress >= mission.target

    fun streakRewardFor(days: Int): Int {
        if (days <= 0) return 0
        val index = (days - 1) % 7
        return STREAK_REWARDS[index]
    }

    fun advanceStreak(lastDateKey: String?, todayKey: String, currentDays: Int): StreakResult {
        if (lastDateKey == todayKey) {
            return StreakResult(days = currentDays, reward = 0, isNewDay = false)
        }
        val days = when {
            lastDateKey == null -> 1
            isYesterday(lastDateKey, todayKey) -> currentDays + 1
            else -> 1
        }
        return StreakResult(days = days, reward = streakRewardFor(days), isNewDay = true)
    }

    private fun isYesterday(lastKey: String, todayKey: String): Boolean = try {
        LocalDate.parse(lastKey).plusDays(1) == LocalDate.parse(todayKey)
    } catch (_: Exception) {
        false
    }
}
