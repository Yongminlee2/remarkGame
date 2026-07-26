package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MissionsTest {

    @Test
    fun `미션 풀은 7종이고 아이디가 겹치지 않는다`() {
        assertEquals(7, Mission.entries.size)
        assertEquals(7, Mission.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `하루에 서로 다른 미션 3개를 뽑는다`() {
        val picked = Missions.pickDaily("2026-07-26")
        assertEquals(3, picked.size)
        assertEquals(3, picked.toSet().size)
    }

    @Test
    fun `같은 날짜면 항상 같은 미션이 나온다`() {
        assertEquals(Missions.pickDaily("2026-07-26"), Missions.pickDaily("2026-07-26"))
    }

    @Test
    fun `날짜가 다르면 조합이 달라진다`() {
        val week = listOf(
            "2026-07-26", "2026-07-27", "2026-07-28", "2026-07-29",
            "2026-07-30", "2026-07-31", "2026-08-01"
        ).map { Missions.pickDaily(it) }
        // 7일치가 전부 같은 조합이면 시드가 날짜를 반영하지 못한 것이다
        assertTrue(week.toSet().size > 1)
    }

    @Test
    fun `누적형 미션은 값을 더한다`() {
        val m = Mission.PLAY_3
        assertEquals(Aggregate.SUM, m.aggregate)
        assertEquals(2, Missions.applyProgress(m, current = 1, amount = 1))
    }

    @Test
    fun `최대형 미션은 더 큰 값만 남긴다`() {
        val m = Mission.ROUNDS_5
        assertEquals(Aggregate.MAX, m.aggregate)
        assertEquals(7, Missions.applyProgress(m, current = 7, amount = 3))
        assertEquals(9, Missions.applyProgress(m, current = 7, amount = 9))
    }

    @Test
    fun `진행도가 목표에 닿으면 완료다`() {
        assertFalse(Missions.isComplete(Mission.PLAY_3, 2))
        assertTrue(Missions.isComplete(Mission.PLAY_3, 3))
        assertTrue(Missions.isComplete(Mission.PLAY_3, 5))
    }

    @Test
    fun `연속 출석 보상은 2일차부터 7일차까지 커진다`() {
        assertEquals(0, Missions.streakRewardFor(1))
        assertEquals(20, Missions.streakRewardFor(2))
        assertEquals(30, Missions.streakRewardFor(3))
        assertEquals(60, Missions.streakRewardFor(6))
        assertEquals(100, Missions.streakRewardFor(7))
    }

    @Test
    fun `연속 출석 보상은 7일 주기로 반복된다`() {
        assertEquals(20, Missions.streakRewardFor(9))   // 9 = 7 + 2일차
        assertEquals(100, Missions.streakRewardFor(14))
    }

    @Test
    fun `첫 플레이는 1일차로 시작한다`() {
        val r = Missions.advanceStreak(lastDateKey = null, todayKey = "2026-07-26", currentDays = 0)
        assertEquals(1, r.days)
        assertTrue(r.isNewDay)
    }

    @Test
    fun `어제 했으면 연속이 이어진다`() {
        val r = Missions.advanceStreak("2026-07-25", "2026-07-26", 3)
        assertEquals(4, r.days)
        assertTrue(r.isNewDay)
    }

    @Test
    fun `같은 날 또 하면 연속이 안 늘어난다`() {
        val r = Missions.advanceStreak("2026-07-26", "2026-07-26", 4)
        assertEquals(4, r.days)
        assertFalse(r.isNewDay)
        assertEquals(0, r.reward)
    }

    @Test
    fun `하루를 건너뛰면 1일차로 초기화된다`() {
        val r = Missions.advanceStreak("2026-07-24", "2026-07-26", 9)
        assertEquals(1, r.days)
        assertTrue(r.isNewDay)
    }

    @Test
    fun `월을 넘겨도 연속 판정이 된다`() {
        val r = Missions.advanceStreak("2026-07-31", "2026-08-01", 2)
        assertEquals(3, r.days)
    }

    @Test
    fun `연속한 두 날은 절대 같은 미션 조합이 나오지 않는다`() {
        var currentDate = LocalDate.parse("2026-01-01")
        for (i in 0 until 400) {
            val today = currentDate.toString()
            val tomorrow = currentDate.plusDays(1).toString()

            val todayTrio = Missions.pickDaily(today).toSet()
            val tomorrowTrio = Missions.pickDaily(tomorrow).toSet()

            assertTrue(
                "consecutive days must have different mission trios at $today and $tomorrow",
                todayTrio != tomorrowTrio
            )
            currentDate = currentDate.plusDays(1)
        }
    }

    @Test
    fun `안 되는 날짜 문자열도 예외 없이 3개를 준다`() {
        val picked = Missions.pickDaily("bogus")
        assertEquals(3, picked.size)
        assertEquals(3, picked.toSet().size)
    }
}
