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
        // 보스 스테이지는 TIME_8 규칙이 기본 곡선을 덮어쓰므로
        // 순수한 곡선은 보스가 아닌 스테이지에서 확인한다
        assertEquals(30, Stage.configFor(1).timerSec)
        assertEquals(20, Stage.configFor(31).timerSec)
        assertEquals(10, Stage.configFor(61).timerSec)
        assertEquals(8, Stage.configFor(67).timerSec)
        assertEquals(8, Stage.configFor(199).timerSec)
    }

    @Test
    fun `1스테이지는 규칙 없이 시작한다`() {
        assertTrue(Stage.configFor(1).rules.isEmpty())
        assertEquals("", Stage.configFor(1).ruleLabel)
    }

    @Test
    fun `2스테이지부터는 모든 스테이지에 특수 조건이 걸린다`() {
        // 조건은 단어 제약이거나 이어가기 방식 변경이다. 둘 중 하나는 반드시 있어야 한다.
        for (n in 2..200) {
            val cfg = Stage.configFor(n)
            val hasSomething = cfg.rules.isNotEmpty() || cfg.chainMode != ChainMode.TAIL
            assertTrue("스테이지 $n 에 아무 조건도 없음", hasSomething)
            assertTrue("스테이지 $n 안내 문구가 비어 있음", cfg.ruleLabel.isNotEmpty())
        }
    }

    @Test
    fun `일반 스테이지는 단어 제약 하나 또는 이어가기 변경 하나만 갖는다`() {
        // 단어 제약과 이어가기 변경을 겹쳐 걸면 손쓸 수 없이 어려워지므로 섞지 않는다.
        for (n in 2..200) {
            if (Stage.isBossStage(n)) continue
            val cfg = Stage.configFor(n)
            if (cfg.chainMode != ChainMode.TAIL) {
                assertTrue("스테이지 $n 은 이어가기 변경인데 단어 제약도 걸림", cfg.rules.isEmpty())
            } else {
                assertEquals("스테이지 $n", 1, cfg.rules.size)
                assertTrue("스테이지 $n 은 단어 제약이어야 함", cfg.rules[0].wordConstraint)
            }
        }
    }

    @Test
    fun `같은 스테이지는 항상 같은 규칙이 나온다`() {
        for (n in listOf(2, 7, 13, 28, 44, 91)) {
            assertEquals(Stage.configFor(n).rules, Stage.configFor(n).rules)
        }
    }

    @Test
    fun `보스 규칙에 서로 모순되는 조합이 없다`() {
        // "두 글자만" + "3글자 이상" 처럼 같이 걸리면 통과할 단어가 하나도 없는 조합을 막는다.
        // 30 이상 보스는 단어 제약 1개 + 압박 규칙 1개로만 구성한다.
        for (n in 30..300 step 5) {
            val cfg = Stage.configFor(n)
            if (cfg.chainMode != ChainMode.TAIL) continue // 이어가기 전용 보스는 단어 규칙이 없다
            val rules = cfg.rules
            assertEquals("스테이지 $n", 2, rules.size)
            assertEquals("스테이지 $n 단어제약", 1, rules.count { it.wordConstraint })
            assertEquals("스테이지 $n 압박규칙", 1, rules.count { !it.wordConstraint })
        }
    }

    @Test
    fun `연달아 붙은 일반 스테이지는 같은 규칙이 나오지 않는다`() {
        // 규칙 종류가 적어서 무작위로 뽑으면 세 판 연속 같은 규칙이 흔하다.
        // 같은 티어 안에서는 반드시 달라야 한다.
        for (n in 2 until 300) {
            if (Stage.isBossStage(n) || Stage.isBossStage(n + 1)) continue
            val a = Stage.configFor(n).let { it.rules to it.chainMode }
            val b = Stage.configFor(n + 1).let { it.rules to it.chainMode }
            // 티어가 바뀌는 경계(9→10, 19→20 등)는 풀 자체가 달라지므로 제외
            if (tierOf(n) != tierOf(n + 1)) continue
            assertTrue("스테이지 $n 과 ${n + 1} 규칙이 같음: $a", a != b)
        }
    }

    private fun tierOf(n: Int) = when {
        n <= 9 -> 0
        n <= 19 -> 1
        else -> 2
    }

    @Test
    fun `초반에는 빡센 제약이 나오지 않는다`() {
        for (n in 2..9) {
            val r = Stage.configFor(n).rules
            assertTrue("스테이지 $n 에 어려운 규칙", BossRule.MIN_LEN_5 !in r && BossRule.EXACT_LEN_4 !in r)
        }
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
    fun `30스테이지 이상 혼합 보스는 서로 다른 규칙 두 개를 갖는다`() {
        // 35·40 은 이어가기 방식을 바꾸는 전용 보스라 단어 규칙을 갖지 않는다
        for (n in listOf(30, 55, 100)) {
            val rules = Stage.configFor(n).boss!!.rules
            assertEquals("스테이지 $n", 2, rules.size)
            assertEquals("스테이지 $n 규칙 중복", 2, rules.toSet().size)
        }
    }

    @Test
    fun `이어가기 전용 보스는 단어 제약을 겹쳐 걸지 않는다`() {
        val reverse = Stage.configFor(35)
        assertEquals(ChainMode.HEAD, reverse.chainMode)
        assertTrue(reverse.rules.isEmpty())
        assertEquals("거꾸로 대왕", reverse.boss!!.name)

        val sameHead = Stage.configFor(40)
        assertEquals(ChainMode.SAME_HEAD, sameHead.chainMode)
        assertTrue(sameHead.rules.isEmpty())
    }

    @Test
    fun `이어가기 변경은 6스테이지부터 나온다`() {
        for (n in 2..5) {
            assertEquals("스테이지 $n", ChainMode.TAIL, Stage.configFor(n).chainMode)
        }
        assertTrue(
            "이어가기 변경이 한 번도 안 나옴",
            (6..40).any { Stage.configFor(it).chainMode != ChainMode.TAIL }
        )
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
