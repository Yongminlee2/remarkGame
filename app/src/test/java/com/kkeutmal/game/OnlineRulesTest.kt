package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * 친구 대전·랭킹 규칙.
 *
 * 서버에서 판정하지 않으므로 이 규칙이 틀리면 **두 폰이 서로 이겼다고 우기거나, 랭킹이
 * 조용히 안 올라간다.** 둘 다 화면에서는 "가끔 이상하다" 로만 보여서 재현이 어렵다.
 *
 * 랭킹 올리기는 `firebase/database.rules.json` 과 짝이라, 앱 계산이 서버 규칙을
 * 넘지 않는지까지 여기서 확인한다.
 */
class OnlineRulesTest {

    // ---------- 방 코드 ----------

    @Test
    fun `방 코드는 헷갈리는 글자 없이 6자리다`() {
        val rng = Random(7)
        repeat(500) {
            val code = OnlineRules.newRoomCode(rng)
            assertEquals(6, code.length)
            assertTrue("헷갈리는 글자가 섞였다: $code", code.none { it in "0O1IL" })
        }
    }

    @Test
    fun `코드 입력은 소문자·띄어쓰기·하이픈을 봐준다`() {
        assertEquals("K7M3PQ", OnlineRules.normalizeCode("k7m3pq"))
        assertEquals("K7M3PQ", OnlineRules.normalizeCode(" K7M-3PQ "))
    }

    @Test
    fun `쓸 수 없는 글자나 길이가 틀리면 받지 않는다`() {
        assertNull(OnlineRules.normalizeCode("K7M3P"))      // 5자리
        assertNull(OnlineRules.normalizeCode("K7M3PQX"))    // 7자리
        assertNull(OnlineRules.normalizeCode("K0M3PQ"))     // 숫자 0
        assertNull(OnlineRules.normalizeCode("KOM3PQ"))     // 알파벳 O
        assertNull(OnlineRules.normalizeCode("K1M3PQ"))     // 숫자 1
    }

    // ---------- 차례와 시간 ----------

    @Test
    fun `남은 시간은 0 과 제한시간 사이로만 나온다`() {
        val t = 1_000_000L
        assertEquals(OnlineRules.TURN_SEC, OnlineRules.secondsLeft(t, t))
        assertEquals(0, OnlineRules.secondsLeft(t, t + 999_999))
        assertEquals(OnlineRules.TURN_SEC, OnlineRules.secondsLeft(t, t - 5_000)) // 서버 시각이 앞서도
    }

    @Test
    fun `상대 시간 초과는 여유까지 지나야 선언한다`() {
        val t = 1_000_000L
        val deadline = OnlineRules.turnDeadline(t)
        assertFalse("마감 직후는 아직이다", OnlineRules.opponentTimedOut(t, deadline + 1))
        assertFalse(OnlineRules.opponentTimedOut(t, deadline + OnlineRules.TURN_GRACE_MS))
        assertTrue(OnlineRules.opponentTimedOut(t, deadline + OnlineRules.TURN_GRACE_MS + 1))
    }

    @Test
    fun `잠깐 끊긴 것은 봐주고 오래 끊기면 기권이다`() {
        val gone = 5_000_000L
        assertFalse(OnlineRules.opponentAbandoned(null, gone + 999_999))
        assertFalse(OnlineRules.opponentAbandoned(gone, gone + OnlineRules.DISCONNECT_GRACE_MS))
        assertTrue(OnlineRules.opponentAbandoned(gone, gone + OnlineRules.DISCONNECT_GRACE_MS + 1))
    }

    // ---------- 랭킹 ----------

    /** database.rules.json 의 ranks 검증식을 그대로 옮긴 것. */
    private fun serverAccepts(oldStage: Int?, oldAt: Long?, newStage: Int, now: Long): Boolean {
        if (newStage < 1 || newStage > 1000) return false
        if (oldStage == null || oldAt == null) return newStage <= 10
        return now - oldAt >= 5000 &&
            newStage >= oldStage &&
            newStage - oldStage <= 20 &&
            newStage <= oldStage + 1 + (now - oldAt).toDouble() / 60000
    }

    @Test
    fun `처음 올릴 때는 10스테이지까지만 올린다`() {
        assertEquals(10, OnlineRules.rankUploadStage(localBest = 57, serverStage = null, serverAt = null, serverNow = 0))
        assertEquals(3, OnlineRules.rankUploadStage(localBest = 3, serverStage = null, serverAt = null, serverNow = 0))
    }

    @Test
    fun `더 높지 않거나 너무 빨리 다시 올리면 올리지 않는다`() {
        assertNull(OnlineRules.rankUploadStage(10, serverStage = 10, serverAt = 0, serverNow = 999_999))
        assertNull(OnlineRules.rankUploadStage(9, serverStage = 10, serverAt = 0, serverNow = 999_999))
        assertNull(OnlineRules.rankUploadStage(40, serverStage = 10, serverAt = 0, serverNow = 1_000))
    }

    @Test
    fun `오래 쉬었다 와도 한 번에 20칸까지만 오른다`() {
        val up = OnlineRules.rankUploadStage(500, serverStage = 10, serverAt = 0, serverNow = 30L * 24 * 3600 * 1000)
        assertEquals(30, up)
    }

    @Test
    fun `앱이 올리는 값은 서버 시계가 조금 뒤처져도 서버 규칙을 넘지 않는다`() {
        val rng = Random(42)
        repeat(20_000) {
            val serverStage: Int? = if (rng.nextInt(5) == 0) null else rng.nextInt(1, 900)
            val serverAt: Long? = serverStage?.let { rng.nextLong(0, 10_000_000) }
            val estimate = (serverAt ?: 0) + rng.nextLong(0, 40L * 60_000)
            val local = rng.nextInt(1, 1000)
            val up = OnlineRules.rankUploadStage(local, serverStage, serverAt, estimate) ?: return@repeat
            // 실제 서버 시각이 앱 추정보다 2초 뒤처진 최악의 경우
            val actualNow = estimate - 2_000
            assertTrue(
                "거부될 값을 올린다: 서버 $serverStage@$serverAt, 로컬 $local, 올림 $up, now $actualNow",
                serverAccepts(serverStage, serverAt, up, actualNow)
            )
        }
    }

    // ---------- 서버 규칙 파일과의 짝 ----------

    private val rules: String by lazy { File("../firebase/database.rules.json").readText() }

    @Test
    fun `서버 규칙 파일이 앱과 같은 숫자를 쓴다`() {
        val ranks = rules.substringAfter("\"ranks\"")
        for (n in listOf(
            "<= ${OnlineRules.RANK_FIRST_MAX})",
            ">= ${OnlineRules.RANK_MIN_INTERVAL_MS}",
            "<= ${OnlineRules.RANK_MAX_JUMP}",
            "/ ${OnlineRules.RANK_MS_PER_STAGE}",
            "<= ${OnlineRules.RANK_STAGE_CAP}"
        )) {
            assertTrue("규칙 파일의 ranks 에 '$n' 이 없다 — 앱과 서버 숫자가 어긋났다", ranks.contains(n))
        }
        assertTrue(
            "규칙 파일의 방 코드 글자 목록이 앱과 다르다",
            rules.contains("[${OnlineRules.CODE_ALPHABET}]{${OnlineRules.CODE_LENGTH}}")
        )
        assertTrue(
            "규칙 파일의 대기방 유효시간이 앱과 다르다",
            rules.contains("<= ${OnlineRules.ROOM_TTL_MS}")
        )
    }

    @Test
    fun `모든 아바타 아이디가 서버 규칙의 형식에 맞는다`() {
        // 안 맞는 아바타를 쓰는 사람은 순위표에 조용히 안 올라가고, 방에도 못 들어간다.
        val pattern = Regex("^[a-z]+_[a-z]+_[a-z]+$")
        assertTrue(rules.contains(pattern.pattern.replace("^", "/^").replace("$", "$/")))
        for (def in AvatarCatalog.ALL) {
            assertTrue("서버 규칙이 받지 않는 아바타 아이디: ${def.id}", pattern.matches(def.id))
        }
        assertNotNull(AvatarCatalog.byId(AvatarCatalog.DEFAULT_ID))
    }
}
