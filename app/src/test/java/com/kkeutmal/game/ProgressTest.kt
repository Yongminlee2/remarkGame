package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    @Test
    fun `레벨별 필요 XP는 50 곱하기 레벨의 1_2제곱`() {
        assertEquals(50, Progress.xpForNextLevel(1))
        assertEquals(114, Progress.xpForNextLevel(2))
        assertEquals(186, Progress.xpForNextLevel(3))
        assertEquals(792, Progress.xpForNextLevel(10))
    }

    @Test
    fun `누적 필요 XP는 이전 레벨들의 합`() {
        assertEquals(0, Progress.totalXpForLevel(1))
        assertEquals(50, Progress.totalXpForLevel(2))
        assertEquals(164, Progress.totalXpForLevel(3))
        assertEquals(350, Progress.totalXpForLevel(4))
    }

    @Test
    fun `누적 XP로 레벨을 역산한다`() {
        assertEquals(1, Progress.levelForTotalXp(0))
        assertEquals(1, Progress.levelForTotalXp(49))
        assertEquals(2, Progress.levelForTotalXp(50))
        assertEquals(2, Progress.levelForTotalXp(163))
        assertEquals(3, Progress.levelForTotalXp(164))
    }

    @Test
    fun `레벨은 99에서 멈춘다`() {
        assertEquals(99, Progress.levelForTotalXp(Int.MAX_VALUE))
    }

    @Test
    fun `현재 레벨 안에서의 XP를 구한다`() {
        assertEquals(0, Progress.xpIntoLevel(50))
        assertEquals(10, Progress.xpIntoLevel(60))
        assertEquals(0, Progress.xpIntoLevel(164))
    }

    @Test
    fun `랭크 경계가 스펙과 일치한다`() {
        assertEquals(Rank.BRONZE, Progress.rankOf(1))
        assertEquals(Rank.BRONZE, Progress.rankOf(9))
        assertEquals(Rank.SILVER, Progress.rankOf(10))
        assertEquals(Rank.SILVER, Progress.rankOf(19))
        assertEquals(Rank.GOLD, Progress.rankOf(20))
        assertEquals(Rank.GOLD, Progress.rankOf(34))
        assertEquals(Rank.PLATINUM, Progress.rankOf(35))
        assertEquals(Rank.PLATINUM, Progress.rankOf(49))
        assertEquals(Rank.DIAMOND, Progress.rankOf(50))
        assertEquals(Rank.DIAMOND, Progress.rankOf(69))
        assertEquals(Rank.MASTER, Progress.rankOf(70))
        assertEquals(Rank.MASTER, Progress.rankOf(89))
        assertEquals(Rank.GRANDMASTER, Progress.rankOf(90))
        assertEquals(Rank.GRANDMASTER, Progress.rankOf(99))
    }

    @Test
    fun `모험 XP가 자유 대전보다 후하다`() {
        // 자유 대전 점수 300점 = 60 XP, 모험 10스테이지 = 150 XP
        assertEquals(60, Progress.freeMatchXp(300))
        assertEquals(150, Progress.stageXp(10, isBoss = false))
        assertEquals(450, Progress.stageXp(10, isBoss = true))
        assertTrue(Progress.stageXp(10, false) > Progress.freeMatchXp(300))
    }
}
