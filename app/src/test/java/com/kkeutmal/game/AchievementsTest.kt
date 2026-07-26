package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private val none = PlayerStats(bestRounds = 0, bestStage = 0, ownedAvatarCount = 0, bestStreak = 0)

    @Test
    fun `도전과제는 4개이고 아바타 카탈로그의 전설 해금 아이디와 일치한다`() {
        assertEquals(4, Achievement.entries.size)
        val fromCatalog = AvatarCatalog.ALL
            .filter { it.grade == AvatarGrade.LEGENDARY }
            .map { (it.unlock as Unlock.Achieve).achievementId }
            .toSet()
        assertEquals(Achievement.entries.map { it.id }.toSet(), fromCatalog)
    }

    @Test
    fun `아무것도 안 했으면 달성한 게 없다`() {
        assertTrue(Achievements.metBy(none).isEmpty())
    }

    @Test
    fun `20라운드 도전과제`() {
        assertFalse(Achievement.ROUNDS_20.isMet(none.copy(bestRounds = 19)))
        assertTrue(Achievement.ROUNDS_20.isMet(none.copy(bestRounds = 20)))
    }

    @Test
    fun `50스테이지 도전과제`() {
        assertFalse(Achievement.STAGE_50.isMet(none.copy(bestStage = 49)))
        assertTrue(Achievement.STAGE_50.isMet(none.copy(bestStage = 50)))
    }

    @Test
    fun `30종 수집 도전과제`() {
        assertFalse(Achievement.COLLECT_30.isMet(none.copy(ownedAvatarCount = 29)))
        assertTrue(Achievement.COLLECT_30.isMet(none.copy(ownedAvatarCount = 30)))
    }

    @Test
    fun `7일 연속 출석 도전과제`() {
        assertFalse(Achievement.STREAK_7.isMet(none.copy(bestStreak = 6)))
        assertTrue(Achievement.STREAK_7.isMet(none.copy(bestStreak = 7)))
    }

    @Test
    fun `달성한 것만 골라 준다`() {
        val stats = PlayerStats(bestRounds = 25, bestStage = 10, ownedAvatarCount = 30, bestStreak = 2)
        assertEquals(
            listOf(Achievement.ROUNDS_20, Achievement.COLLECT_30),
            Achievements.metBy(stats)
        )
    }

    @Test
    fun `아이디로 라벨을 찾는다`() {
        assertEquals("50스테이지 도달", Achievements.labelOf("ach_stage_50"))
    }
}
