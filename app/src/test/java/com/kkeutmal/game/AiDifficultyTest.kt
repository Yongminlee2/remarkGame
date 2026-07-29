package com.kkeutmal.game

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 난이도가 실제로 난이도답게 오르는지 본다.
 *
 * "AI가 어떤 단어를 주는가"는 결국 **내가 이어갈 수 있는 말이 몇 개나 남는가**로 체감된다.
 * 그래서 난이도별로 AI가 낸 단어의 후속 단어 수를 재서, 쉬운 쪽이 확실히 넉넉한지 확인한다.
 *
 * 사전을 실제로 올려야 의미가 있어서 assets 를 직접 읽는다.
 */
class AiDifficultyTest {

    companion object {
        private val assets = File("src/main/assets")

        @BeforeClass
        @JvmStatic
        fun loadDict() {
            if (assets.isDirectory) WordDict.loadWordsForTest(assets)
        }
    }

    private fun requireDict() =
        assumeTrue("사전 자산을 못 찾음: ${assets.absolutePath}", WordDict.ready)

    /** 그 난이도 AI 가 한 판을 두는 동안 낸 단어들의 '후속 단어 수' 평균 */
    private fun averageRoom(level: AiLevel, games: Int = 12, turns: Int = 5): Double {
        var sum = 0.0
        var n = 0
        repeat(games) {
            val e = GameEngine(level, noTimer = true)
            e.applyWord(e.openingWord())
            repeat(turns) {
                val mine = firstValidReply(e) ?: return@repeat
                e.acceptPlayer(mine, 10)
                val ai = e.aiMove() ?: return@repeat
                e.applyWord(ai)
                sum += e.followUpCount(ai, 60)
                n++
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    private fun firstValidReply(e: GameEngine): String? {
        val starts = e.allowedStarts() ?: return null
        return e.candidatesUnderRules(starts, common = true).firstOrNull()
            ?: e.candidatesUnderRules(starts, common = false).firstOrNull()
    }

    @Test
    fun `쉬운 난이도일수록 이어갈 말을 넉넉히 준다`() {
        requireDict()
        val veryEasy = averageRoom(AiLevel.VERY_EASY)
        val easy = averageRoom(AiLevel.EASY)
        val normal = averageRoom(AiLevel.NORMAL)

        val msg = "매우쉬움 %.1f / 쉬움 %.1f / 보통 %.1f".format(veryEasy, easy, normal)
        assertTrue("매우쉬움이 쉬움보다 넉넉해야 한다 — $msg", veryEasy >= easy)
        assertTrue("쉬움이 보통보다 넉넉해야 한다 — $msg", easy >= normal)
    }

    @Test
    fun `매우쉬움과 쉬움은 한방단어를 절대 내지 않는다`() {
        requireDict()
        for (level in listOf(AiLevel.VERY_EASY, AiLevel.EASY)) {
            repeat(15) {
                val e = GameEngine(level, noTimer = true)
                e.applyWord(e.openingWord())
                repeat(10) {
                    val mine = firstValidReply(e) ?: return@repeat
                    e.acceptPlayer(mine, 10)
                    val ai = e.aiMove() ?: return@repeat
                    e.applyWord(ai)
                    assertTrue(
                        "$level 이 한방단어 '$ai' 를 냈다",
                        e.followUpCount(ai, 1) >= 1
                    )
                }
            }
        }
    }

    @Test
    fun `한방단어를 막아 둔 판에서는 보통도 어려움도 한방단어를 안 낸다`() {
        requireDict()
        // 모험 16~30스테이지가 이 경우다 — AI 는 '보통' 인데 한방단어는 아직 이르다.
        for (level in listOf(AiLevel.NORMAL, AiLevel.HARD)) {
            repeat(12) {
                val e = GameEngine(level, noTimer = true, allowHanbang = false)
                e.applyWord(e.openingWord())
                repeat(14) {
                    val mine = firstValidReply(e) ?: return@repeat
                    e.acceptPlayer(mine, 10)
                    val ai = e.aiMove() ?: return@repeat
                    e.applyWord(ai)
                    assertTrue(
                        "$level 이 한방단어 금지인데 '$ai' 를 냈다 (라운드 ${e.round})",
                        e.followUpCount(ai, 1) >= 1
                    )
                }
            }
        }
    }

    @Test
    fun `제한시간은 난이도가 오를수록 짧아진다`() {
        val secs = AiLevel.entries.map { it.timerSec }
        assertTrue("제한시간이 단조 감소하지 않는다: $secs", secs.zipWithNext().all { it.first > it.second })
        // 한 단계 건너뛸 때 절반 아래로 뚝 떨어지면 체감이 급격해진다
        for ((a, b) in secs.zipWithNext()) {
            assertTrue("$a 초 → $b 초 는 낙차가 너무 크다", b * 2 > a)
        }
    }
}
