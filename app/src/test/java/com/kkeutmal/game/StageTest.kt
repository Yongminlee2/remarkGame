package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageTest {

    @Test
    fun `제한시간은 30초에서 시작해 8초까지 줄어든다`() {
        assertEquals(30, Stage.configFor(1).timerSec)
        assertEquals(20, Stage.configFor(30).timerSec)
        assertEquals(10, Stage.configFor(60).timerSec)
        assertEquals(8, Stage.configFor(66).timerSec)
        assertEquals(8, Stage.configFor(200).timerSec)
    }

    @Test
    fun `AI 레벨이 구간별로 승격된다`() {
        assertEquals(AiLevel.VERY_EASY, Stage.configFor(1).aiLevel)
        assertEquals(AiLevel.VERY_EASY, Stage.configFor(5).aiLevel)
        assertEquals(AiLevel.EASY, Stage.configFor(6).aiLevel)
        assertEquals(AiLevel.EASY, Stage.configFor(15).aiLevel)
        assertEquals(AiLevel.NORMAL, Stage.configFor(16).aiLevel)
        assertEquals(AiLevel.NORMAL, Stage.configFor(30).aiLevel)
        assertEquals(AiLevel.HARD, Stage.configFor(31).aiLevel)
    }

    @Test
    fun `목표 라운드는 3에서 시작해 3스테이지마다 하나씩 느는다`() {
        assertEquals(3, Stage.configFor(1).targetRounds)
        assertEquals(4, Stage.configFor(3).targetRounds)
        assertEquals(13, Stage.configFor(30).targetRounds)
        assertEquals(23, Stage.configFor(60).targetRounds)
    }

    @Test
    fun `한방단어는 31스테이지부터 허용된다`() {
        assertFalse(Stage.configFor(30).allowHanbang)
        assertTrue(Stage.configFor(31).allowHanbang)
    }

    @Test
    fun `보스는 5스테이지마다 나온다`() {
        assertNull(Stage.configFor(4).boss)
        assertNotNull(Stage.configFor(5).boss)
        assertNull(Stage.configFor(6).boss)
        assertNotNull(Stage.configFor(10).boss)
        assertTrue(Stage.isBossStage(25))
        assertFalse(Stage.isBossStage(26))
    }

    @Test
    fun `지정된 보스는 스펙과 일치한다`() {
        assertEquals(listOf(BossRule.MIN_LEN_3), Stage.configFor(5).boss!!.rules)
        assertEquals(listOf(BossRule.ENDS_WITH_JONGSEONG), Stage.configFor(10).boss!!.rules)
        assertEquals(listOf(BossRule.TIME_8), Stage.configFor(15).boss!!.rules)
        assertEquals(listOf(BossRule.AI_HANBANG), Stage.configFor(20).boss!!.rules)
        assertEquals(listOf(BossRule.MIN_LEN_4), Stage.configFor(25).boss!!.rules)
        assertEquals("세글자 도깨비", Stage.configFor(5).boss!!.name)
    }

    @Test
    fun `시간 도둑 보스는 제한시간을 8초로 덮어쓴다`() {
        // 15스테이지의 기본 제한시간은 25초지만 보스 규칙이 8초로 고정한다
        assertEquals(8, Stage.configFor(15).timerSec)
    }

    @Test
    fun `30스테이지 이상 보스는 서로 다른 규칙 두 개를 갖는다`() {
        for (n in listOf(30, 35, 40, 55, 100)) {
            val rules = Stage.configFor(n).boss!!.rules
            assertEquals("스테이지 $n", 2, rules.size)
            assertEquals("스테이지 $n 규칙 중복", 2, rules.toSet().size)
        }
    }

    @Test
    fun `혼합 보스는 같은 스테이지면 항상 같은 규칙이 나온다`() {
        repeat(5) {
            assertEquals(Stage.configFor(30).boss!!.rules, Stage.configFor(30).boss!!.rules)
            assertEquals(Stage.configFor(75).boss!!.rules, Stage.configFor(75).boss!!.rules)
        }
    }

    @Test
    fun `다음 보스까지 남은 스테이지 수`() {
        assertEquals(4, Stage.stagesToNextBoss(1))
        assertEquals(1, Stage.stagesToNextBoss(4))
        assertEquals(0, Stage.stagesToNextBoss(5))
        assertEquals(3, Stage.stagesToNextBoss(12))
    }

    @Test
    fun `0 이하 스테이지는 다음 보스까지 5로 본다`() {
        assertEquals(5, Stage.stagesToNextBoss(0))
        assertEquals(5, Stage.stagesToNextBoss(-1))
        assertEquals(0, Stage.stagesToNextBoss(5))
    }
}
