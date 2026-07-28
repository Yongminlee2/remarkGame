package com.kkeutmal.game

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 진짜 사전을 올려 놓고 GameEngine 을 돌려 보는 테스트.
 *
 * 여기서 잡는 버그들은 규칙 표만 봐서는 안 보인다 — "첫 단어가 한방단어였다",
 * "단어를 바꿨는데 이어야 할 글자가 그대로였다" 같은 것들은 43만 단어를 실제로
 * 훑어 봐야 드러난다. 그래서 assets 를 직접 읽어 사전을 올린다.
 */
class GameEngineDictTest {

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

    // ── 첫 단어는 어느 난이도에서도 한방단어가 아니어야 한다 ──────────────

    @Test
    fun `첫 단어는 모든 난이도에서 이어갈 단어가 있다`() {
        requireDict()
        for (level in AiLevel.entries) {
            repeat(40) {
                val e = GameEngine(level, noTimer = false)
                val w = e.openingWord()
                assertTrue(
                    "$level 의 첫 단어 '$w' 뒤에 이을 단어가 없다",
                    e.followUpCount(w, 1) >= 1
                )
            }
        }
    }

    @Test
    fun `첫 단어는 이어갈 단어가 넉넉하다`() {
        requireDict()
        repeat(60) {
            val e = GameEngine(AiLevel.HARD, noTimer = false)
            val w = e.openingWord()
            assertTrue("첫 단어 '$w' 의 후속이 너무 적다", e.followUpCount(w, 10) >= 5)
        }
    }

    /**
     * 실제로 신고된 버그: "세 글자·네 글자 규칙 스테이지에서 AI 첫 단어가 한방단어라
     * 손도 못 써 보고 졌다."
     *
     * 원인은 길이 제약이 걸리면 상용 단어 뽑기 갈래가 통째로 무산되고
     * (2~3글자를 찾는데 규칙은 4글자를 요구하니 절대 안 맞는다)
     * 그 뒤의 예비 갈래가 **후속 단어를 세지 않은 채** 규칙만 맞으면 돌려줬기 때문이다.
     */
    @Test
    fun `길이 규칙이 걸려도 첫 단어는 한방단어가 아니다`() {
        requireDict()
        val ruleSets = listOf(
            listOf(BossRule.EXACT_LEN_2),
            listOf(BossRule.MIN_LEN_3),
            listOf(BossRule.EXACT_LEN_3),
            listOf(BossRule.MIN_LEN_4),
            listOf(BossRule.EXACT_LEN_4),
            listOf(BossRule.ENDS_WITH_JONGSEONG),
            listOf(BossRule.NO_JONGSEONG),
            listOf(BossRule.EXACT_LEN_4, BossRule.TIME_8),
            listOf(BossRule.MIN_LEN_4, BossRule.AI_HANBANG)
        )
        for (rules in ruleSets) {
            repeat(50) {
                val e = GameEngine(AiLevel.NORMAL, noTimer = false, bossRules = rules)
                val w = e.openingWord()
                assertTrue("$rules 에서 첫 단어 '$w' 가 규칙 위반", rules.acceptsWord(w))
                assertTrue("$rules 에서 첫 단어 '$w' 뒤가 막힘", e.followUpCount(w, 1) >= 1)
            }
        }
    }

    @Test
    fun `이어가기 방식이 바뀌어도 첫 단어는 막히지 않는다`() {
        requireDict()
        for (mode in ChainMode.entries) {
            repeat(20) {
                val e = GameEngine(AiLevel.NORMAL, noTimer = false, chainMode = mode)
                val w = e.openingWord()
                assertTrue("$mode 의 첫 단어 '$w' 뒤가 막힘", e.followUpCount(w, 1) >= 1)
            }
        }
    }

    // ── 단어 바꾸기는 이어야 할 글자를 실제로 바꿔야 한다 ────────────────

    @Test
    fun `단어를 바꾸면 이어야 할 글자가 달라진다`() {
        requireDict()
        var changed = 0
        var tried = 0
        repeat(80) {
            val e = GameEngine(AiLevel.NORMAL, noTimer = false)
            e.applyWord(e.openingWord())
            e.acceptPlayer(firstValidReply(e), 10)
            e.applyWord(e.aiMove() ?: return@repeat)

            val before = e.allowedStarts()!!
            val newWord = e.rerollAiWord() ?: return@repeat
            tried++
            assertTrue("바꾼 단어 '$newWord' 뒤가 막혔다", e.followUpCount(newWord, 1) >= 1)
            if (e.allowedStarts()!!.none { it in before }) changed++
        }
        assumeTrue("교체가 한 번도 안 일어남", tried > 0)
        // 일부러 피해서 고르므로 거의 항상 바뀌어야 한다.
        // 무작위로 뽑던 예전 코드는 여기서 걸린다.
        assertTrue("이어야 할 글자가 자주 그대로다 ($changed/$tried)", changed >= tried * 0.95)
    }

    @Test
    fun `첫 단어 직후에 바꿔도 단어가 교체된다`() {
        requireDict()
        repeat(20) {
            val e = GameEngine(AiLevel.NORMAL, noTimer = false)
            val first = e.openingWord()
            e.applyWord(first)
            val newWord = e.rerollAiWord()
            assertNotNull("첫 단어 직후 교체 실패", newWord)
            assertNotEquals(first, newWord)
            assertTrue("바뀐 첫 단어 '$newWord' 뒤가 막힘", e.followUpCount(newWord!!, 1) >= 1)
        }
    }

    @Test
    fun `바꾼 단어는 이어가기 방식마다 내 단어에 제대로 이어진다`() {
        requireDict()
        // 예전 코드는 이어가기 방식과 무관하게 '내 단어의 끝 글자'로만 이었다.
        // 앞말잇기·같은글자 판에서는 그게 규칙 위반이라 여기서 걸린다.
        for (mode in ChainMode.entries) {
            var tried = 0
            repeat(25) {
                val e = GameEngine(AiLevel.NORMAL, noTimer = false, chainMode = mode)
                e.applyWord(e.openingWord())
                val mine = firstValidReply(e)
                e.acceptPlayer(mine, 10)
                e.applyWord(e.aiMove() ?: return@repeat)

                val newWord = e.rerollAiWord() ?: return@repeat
                tried++
                // 바뀐 AI 단어는 '내 단어'에 이어붙는 자리를 지켜야 한다
                val legal = mode.linkSyllables(mine)
                assertTrue(
                    "$mode: '$mine' 다음에 '$newWord' 는 규칙 위반 " +
                        "(맞춰야 할 글자 $legal, 실제 ${mode.anchorOf(newWord)})",
                    mode.anchorOf(newWord) in legal
                )
                assertTrue("$mode: 바꾼 단어 '$newWord' 뒤가 막힘", e.followUpCount(newWord, 1) >= 1)
            }
            assumeTrue("$mode 에서 교체가 한 번도 안 일어남", tried > 0)
        }
    }

    /** 지금 이을 수 있는 단어 하나 — 테스트에서 플레이어 역할을 대신한다 */
    private fun firstValidReply(e: GameEngine): String {
        val starts = e.allowedStarts()!!
        return e.candidatesUnderRules(starts, common = true).firstOrNull()
            ?: e.candidatesUnderRules(starts, common = false).first()
    }
}
