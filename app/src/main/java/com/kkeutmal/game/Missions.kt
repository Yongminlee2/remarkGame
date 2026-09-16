package com.kkeutmal.game

import android.content.Context
import java.time.LocalDate
import kotlin.random.Random

enum class Aggregate { SUM, MAX }

enum class Mission(
    val id: String,
    val label: String,
    val target: Int,
    val reward: Int,
    val aggregate: Aggregate,
    /** 인터넷이 있어야 깰 수 있는 미션. 하루에 하나까지만 뽑는다. */
    val online: Boolean = false
) {
    // id 는 저장된 진행도의 키라 바꾸면 안 된다. 이름표(label)는 바꿔도 된다.
    PLAY_3("m_play3", "게임 3판 하기", 3, 30, Aggregate.SUM),
    ROUNDS_5("m_rounds5", "AI 대전 5라운드 이상 버티기", 5, 40, Aggregate.MAX),
    LONG_WORD_3("m_long3", "4글자 이상 단어 3번 쓰기", 3, 40, Aggregate.SUM),
    STAGE_2("m_stage2", "스테이지 도전 2개 깨기", 2, 50, Aggregate.SUM),
    SURRENDER_1("m_surrender1", "AI 항복시키기", 1, 50, Aggregate.SUM),
    VOICE_5("m_voice5", "음성으로 단어 5번 내기", 5, 40, Aggregate.SUM),
    SCORE_300("m_score300", "AI 대전 누적 300점 얻기", 300, 30, Aggregate.SUM),
    STREAK_3("m_streak3", "AI 대전 3연승 하기", 3, 60, Aggregate.MAX),
    ITEM_2("m_item2", "아이템 2번 쓰기", 2, 30, Aggregate.SUM),
    ONLINE_PLAY_1("m_online1", "온라인 대전 1판 하기", 1, 40, Aggregate.SUM, online = true),
    ONLINE_WIN_1("m_onlinewin1", "온라인 대전에서 이기기", 1, 60, Aggregate.SUM, online = true)
}

data class StreakResult(val days: Int, val reward: Int, val isNewDay: Boolean)

/** 지금 줘야 할 미션 보상. [paid] 는 이번에 받음 표시를 할 미션들. */
data class MissionPayout(val coins: Int, val paid: List<Mission>, val bonus: Boolean)

/** 일일 미션과 연속 출석. 날짜는 yyyy-MM-dd 문자열로 주고받아 테스트 가능하게 한다. */
object Missions {
    const val ALL_CLEAR_BONUS = 50

    private val STREAK_REWARDS = listOf(0, 20, 30, 40, 50, 60, 100) // 1~7일차

    /**
     * 시드로 섞어 앞에서부터 3개. **온라인 미션은 하루에 하나까지** — 인터넷이 안 되는 날
     * 세 개 중 둘을 못 깨는 일이 없게.
     */
    private fun trio(seed: Long): List<Mission> {
        val out = ArrayList<Mission>(3)
        for (m in Mission.entries.shuffled(Random(seed))) {
            if (m.online && out.any { it.online }) continue
            out += m
            if (out.size == 3) break
        }
        return out
    }

    fun pickDaily(dateKey: String): List<Mission> {
        val todayTrio = trio(dateKey.hashCode().toLong())

        // Compute yesterday's plain seeded trio for comparison (do NOT apply anti-repeat logic to avoid deep recursion)
        val yesterdayTrio = try {
            val yesterday = LocalDate.parse(dateKey).minusDays(1).toString()
            trio(yesterday.hashCode().toLong())
        } catch (_: Exception) {
            return todayTrio  // If parsing fails, return today's trio as-is
        }

        // If today's plain trio matches yesterday's plain trio, find a perturbed seed that differs
        if (todayTrio.toSet() == yesterdayTrio.toSet()) {
            // Try increasingly aggressive perturbations to find a different trio
            for (perturb in 1L..200L) {
                val perturbedSeed = dateKey.hashCode().toLong() * 31L + perturb
                val perturbedTrio = trio(perturbedSeed)
                if (perturbedTrio.toSet() != yesterdayTrio.toSet()) {
                    return perturbedTrio
                }
            }
            // If none of the perturbations work, use the first one anyway
            return trio(dateKey.hashCode().toLong() * 31L + 1)
        }

        return todayTrio
    }

    fun applyProgress(mission: Mission, current: Int, amount: Int): Int =
        when (mission.aggregate) {
            Aggregate.SUM -> current + amount
            Aggregate.MAX -> maxOf(current, amount)
        }

    fun isComplete(mission: Mission, progress: Int): Boolean = progress >= mission.target

    /**
     * 깼는데 아직 안 받은 보상을 계산한다. **받음 표시와 함께 써야 두 번 받지 않는다.**
     * 셋 다 깨면 [ALL_CLEAR_BONUS] 를 한 번 더 준다.
     */
    fun payout(
        missions: List<Mission>,
        progress: (Mission) -> Int,
        alreadyPaid: (Mission) -> Boolean,
        bonusPaid: Boolean
    ): MissionPayout {
        val due = missions.filter { isComplete(it, progress(it)) && !alreadyPaid(it) }
        val allDone = missions.isNotEmpty() && missions.all { isComplete(it, progress(it)) }
        val bonus = allDone && !bonusPaid
        return MissionPayout(due.sumOf { it.reward } + (if (bonus) ALL_CLEAR_BONUS else 0), due, bonus)
    }

    /** 미션 진행도를 올린다. 오늘 뽑히지 않은 미션도 올려 두지만 날이 바뀌면 메인 화면이 초기화한다. */
    fun bump(ctx: Context, mission: Mission, amount: Int) {
        if (amount <= 0) return
        val prefs = ctx.getSharedPreferences("kkeutmal", Context.MODE_PRIVATE)
        val key = "mission_progress_${mission.id}"
        prefs.edit().putInt(key, applyProgress(mission, prefs.getInt(key, 0), amount)).apply()
    }

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
