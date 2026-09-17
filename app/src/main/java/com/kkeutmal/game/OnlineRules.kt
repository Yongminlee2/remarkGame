package com.kkeutmal.game

import kotlin.random.Random

/**
 * 온라인 대전·랭킹 규칙 중 **서버와 화면을 타지 않는 부분**.
 *
 * 이 앱은 서버에서 판정하지 않는다(무료 요금제라 Cloud Functions 가 없다). 그래서 **두 폰이
 * 같은 규칙으로 같은 결론을 내야 한다.** 규칙이 여기저기 흩어져 한쪽 폰만 달라지면 서로
 * "내가 이겼다" 를 주장하게 된다. 한 곳에 모아 테스트로 굳혀 둔다.
 *
 * 랭킹 올리기 규칙은 `firebase/database.rules.json` 과 **짝**이다. 한쪽만 고치면
 * 앱이 올리는 값을 서버가 거부하거나, 서버가 조작을 못 막는다.
 */
object OnlineRules {

    // ---------- 방 코드 ----------

    /** 헷갈리는 글자(0·O·1·I·L)를 뺐다. 말로 불러 주거나 손으로 옮겨 적을 때 틀리지 않게. */
    const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val CODE_LENGTH = 6

    /** 대기방이 살아 있는 시간. 지나면 그 코드로는 못 들어온다. */
    const val ROOM_TTL_MS = 10 * 60_000L

    fun newRoomCode(rng: Random = Random.Default): String =
        (1..CODE_LENGTH).map { CODE_ALPHABET[rng.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    /**
     * 사용자가 친 코드를 정리한다. 소문자·띄어쓰기·하이픈은 봐주고, 쓸 수 없는 글자가 있으면 null.
     */
    fun normalizeCode(input: String): String? {
        val s = input.uppercase().filter { !it.isWhitespace() && it != '-' }
        if (s.length != CODE_LENGTH) return null
        if (s.any { it !in CODE_ALPHABET }) return null
        return s
    }

    fun roomExpired(createdAt: Long, serverNow: Long): Boolean = serverNow - createdAt > ROOM_TTL_MS

    // ---------- 차례와 시간 ----------

    /** 온라인 대전 한 차례 제한시간. 자유 대전 '보통' 과 같다. */
    const val TURN_SEC = 25

    /**
     * 상대의 시간 초과를 선언하기 전에 더 기다리는 여유.
     * 상대가 마감 직전에 낸 단어는 네트워크를 건너오느라 조금 늦게 도착한다.
     * **이기는 쪽이 선언하는 판정이라 관대해야 한다** — 박하게 잡으면 억울한 패배가 생긴다.
     */
    const val TURN_GRACE_MS = 3_000L

    /** 연결이 끊긴 뒤 패배로 치기까지 기다리는 시간. 지하철 같은 잠깐 끊김은 봐준다. */
    const val DISCONNECT_GRACE_MS = 10_000L

    /**
     * @param extraMs 이번 차례에 시간 아이템으로 늘린 시간. 방의 `extra` 칸 값이고,
     *   차례가 넘어가면 서버에서 지워져 0 이 된다. 두 폰이 같은 값을 보고 같은 마감을 낸다.
     */
    fun turnDeadline(turnAt: Long, extraMs: Long = 0L): Long = turnAt + TURN_SEC * 1000L + extraMs

    /** 화면에 보여 줄 남은 초. */
    fun secondsLeft(turnAt: Long, serverNow: Long, extraMs: Long = 0L): Int =
        ((turnDeadline(turnAt, extraMs) - serverNow + 999) / 1000).toInt()
            .coerceIn(0, TURN_SEC + (extraMs / 1000).toInt())

    /** 상대 차례가 여유까지 다 넘겼는가. 넘겼으면 내가 이겼다고 선언해도 된다. */
    fun opponentTimedOut(turnAt: Long, serverNow: Long, extraMs: Long = 0L): Boolean =
        serverNow > turnDeadline(turnAt, extraMs) + TURN_GRACE_MS

    // ---------- 아이템 ----------
    //
    // 온라인에서는 **종류마다 한 판에 한 번**. 서버 규칙이 `used/내uid/종류` 를 한 번만 받아서,
    // 조작한 앱도 두 번은 못 쓴다(아이템 개수 자체는 폰에만 있어 서버가 모른다).
    // 부활은 판을 되돌리는 것이라 상대가 있는 대전에 안 맞고, 2배는 온라인에 보상이 없어 뺐다.
    // 힌트는 내 폰에서만 보는 것이라 서버에 적지 않는다(한 번 제한도 폰에서).

    object Item {
        const val TIME = "time"
        const val PASS = "pass"
    }

    /** 시간 아이템이 늘려 주는 시간. 규칙 파일의 `=== 15000` 과 짝이다. */
    const val TIME_ITEM_MS = 15_000L

    /** 상대에게 보여 주는 "쓰는 중" 글자 수 상한. 단어 최대 길이와 같다. */
    const val TYPING_MAX = 20

    /** 상대 연결이 끊긴 지 여유보다 오래됐는가. goneSince 가 null 이면 연결돼 있다. */
    fun opponentAbandoned(goneSince: Long?, serverNow: Long): Boolean =
        goneSince != null && serverNow - goneSince > DISCONNECT_GRACE_MS

    // ---------- 결과 선언 ----------

    /**
     * 결과에 적는 까닭. 서버 규칙이 20자까지만 받으므로 짧은 영문 코드로 적고,
     * 화면 문구는 [resultMessage] 가 내 쪽에서 본 말로 바꾼다.
     */
    object Reason {
        const val TIMEOUT = "timeout"
        const val LEFT = "left"
        const val SURRENDER = "surrender"
        const val HANBANG = "hanbang"
        const val INVALID = "invalid"
        val ALL = listOf(TIMEOUT, LEFT, SURRENDER, HANBANG, INVALID)
    }

    sealed class Claim {
        data object None : Claim()
        data class Win(val reason: String) : Claim()
        data class Lose(val reason: String) : Claim()
    }

    /**
     * 지금 내가 결과를 선언해야 하는가. **두 폰이 같은 상태를 보면 같은 답을 낸다.**
     *
     * 지는 선언(내 시간 초과)은 마감에 바로 한다 — 내가 늦게 선언하면 그만큼 시간을 번다.
     * 이기는 선언(상대 시간 초과·나감)은 여유를 둔다 — 상대 단어가 오는 중일 수 있다.
     * 나감을 가장 먼저 본다. 상대가 사라진 사이에 내 시간이 다 된 것을 패배로 치면 억울하다.
     */
    fun judge(
        me: String,
        turn: String?,
        turnAt: Long,
        opponentGoneSince: Long?,
        resultDecided: Boolean,
        serverNow: Long,
        extraMs: Long = 0L
    ): Claim {
        if (resultDecided || turn == null || turnAt <= 0L) return Claim.None
        if (opponentAbandoned(opponentGoneSince, serverNow)) return Claim.Win(Reason.LEFT)
        if (turn == me && serverNow >= turnDeadline(turnAt, extraMs)) return Claim.Lose(Reason.TIMEOUT)
        if (turn != me && opponentTimedOut(turnAt, serverNow, extraMs)) return Claim.Win(Reason.TIMEOUT)
        return Claim.None
    }

    /** 결과를 내 쪽에서 본 문장으로. */
    fun resultMessage(reason: String?, iWon: Boolean): String = when (reason) {
        Reason.TIMEOUT -> if (iWon) "상대가 시간을 넘겼어요" else "시간을 넘겼어요"
        Reason.LEFT -> if (iWon) "상대가 나갔어요" else "연결이 끊겨 패배했어요"
        Reason.SURRENDER -> if (iWon) "상대가 항복했어요" else "항복했어요"
        Reason.HANBANG -> if (iWon) "한방단어로 이겼어요" else "한방단어에 당했어요"
        Reason.INVALID -> if (iWon) "상대가 규칙에 맞지 않는 단어를 냈어요" else "규칙에 맞지 않는 단어라 패배했어요"
        else -> if (iWon) "이겼어요" else "졌어요"
    }

    // ---------- 랜덤 매칭 ----------
    //
    // 서버 코드 없이 대기열(queue) 하나로 짝을 짓는다. 기다리는 사람은 대기열에 칸을 올리고,
    // 뒤에 온 사람이 그 칸에 방 코드를 적어 "찜" 한다. **뒤에 온 쪽만 찜한다** — 둘이 동시에
    // 서로를 찜해 방이 두 개 생기는 일을 이 순서 하나로 막는다. 같은 칸을 둘이 찜하는 경쟁은
    // 서버 트랜잭션이 한 명만 이기게 한다.

    /** 이만큼 기다려도 상대가 없으면 AI 와 붙는다. */
    const val MATCH_WAIT_MS = 20_000L

    /** 기다리는 동안 대기열을 다시 훑는 간격. 동시에 들어와 둘 다 칸만 올린 경우를 푼다. */
    const val MATCH_RECHECK_MS = 3_000L

    /** 찜한 사람이 이 안에 방에 안 들어오면 방을 지우고 다시 찾는다(그새 나갔다). */
    const val MATCH_JOIN_TIMEOUT_MS = 10_000L

    /** 이보다 오래된 칸은 꺼진 앱이 남긴 것으로 보고 건너뛴다. [MATCH_WAIT_MS] 보다 넉넉해야 한다. */
    const val QUEUE_STALE_MS = 40_000L

    /** 대기열에서 한 번에 읽는 칸 수(오래된 순). */
    const val QUEUE_SCAN = 20

    data class QueueEntry(val uid: String, val at: Long, val claimed: Boolean)

    /**
     * 찜할 상대를 고른다. 없으면 null — 그러면 내가 칸을 올리고 기다린다.
     *
     * @param myAt 내가 대기열에 올린 시각. 아직 안 올렸으면 null(누구든 찜해도 된다).
     *   올렸으면 **나보다 먼저 온 사람만** 고른다. 시각이 같으면 uid 로 순서를 정한다.
     */
    fun pickOpponent(entries: List<QueueEntry>, me: String, myAt: Long?, serverNow: Long): String? =
        entries
            .filter { it.uid != me && !it.claimed && serverNow - it.at <= QUEUE_STALE_MS }
            .filter { myAt == null || it.at < myAt || (it.at == myAt && it.uid < me) }
            .minWithOrNull(compareBy<QueueEntry>({ it.at }, { it.uid }))
            ?.uid

    // ---------- 온라인 대전 순위 ----------
    //
    // **폰의 전적이 아니라 서버가 판마다 센 기록**으로 매긴다. 폰의 전적은 광고로 패배를 지울 수
    // 있어 순위에 쓰면 안 된다. 판이 끝나면 두 폰이 다 세러 가고, 서버 규칙이 방마다 한 번만 받는다.
    //
    // 세는 판: **랜덤 매칭 방**(invited 가 있는 방)이고, 방이 생기고 [RANKED_MIN_MATCH_MS] 이상 지나 결과가 난 판.
    // 친구 코드 방은 둘이 짜고 승수를 쌓기가 너무 쉬워 뺐다. 이긴 기록은 [RANKED_WIN_GAP_MS] 에 한 번만 받는다.
    //
    // ponytail: 서버가 판정하지 않으므로 조작한 앱이 진짜 상대에게 억지 승리를 선언하거나,
    // 두 계정으로 방을 직접 만들어 짜고 치는 것은 늦출 뿐 못 막는다. 막으려면 Cloud Functions 에서
    // 방의 단어 기록을 다시 검증해야 한다(유료 요금제).

    /** 이보다 빨리 끝난 판은 순위에 안 센다. 규칙 파일의 `>= 20000` 과 짝. */
    const val RANKED_MIN_MATCH_MS = 20_000L

    /** 한 사람의 이긴 기록을 다시 받기까지의 간격. 규칙 파일의 `>= 45000` 과 짝. */
    const val RANKED_WIN_GAP_MS = 45_000L

    fun countsForRanking(randomMatch: Boolean, createdAt: Long, resultAt: Long): Boolean =
        randomMatch && createdAt > 0L && resultAt - createdAt >= RANKED_MIN_MATCH_MS

    data class OnlineRankEntry(
        val uid: String,
        val avatar: String?,
        val wins: Int,
        val losses: Int,
        val shown: Boolean
    )

    /** 순위표에 올릴 줄. 올리기를 고른 사람만, 한 번이라도 이긴 사람만. 승수 많은 순, 같으면 패가 적은 순. */
    fun onlineLeaderboard(entries: List<OnlineRankEntry>, limit: Int): List<OnlineRankEntry> =
        entries.filter { it.shown && it.wins > 0 }
            .sortedWith(compareByDescending<OnlineRankEntry> { it.wins }.thenBy { it.losses }.thenBy { it.uid })
            .take(limit)

    // ---------- 랭킹 ----------
    //
    // 랭킹은 **모험 최고 스테이지**로 매긴다. 온라인 대전 결과로 매기면 폰 두 대로 짜고 쳐서
    // 1등이 되고, 대전 패배는 광고로 지울 수 있어 의미가 없어진다. 모험은 AI 상대라 짜고 칠
    // 수 없고 한 칸씩만 오르므로, 서버 규칙으로 "오르는 속도" 를 묶을 수 있다.
    //
    // ponytail: 서버 검증이 아니라 속도 제한이다. 조작한 앱은 시간을 들이면 순위를 올릴 수
    // 있다 — 늦출 뿐 못 막는다. 제대로 막으려면 Cloud Functions(유료 요금제)에서 판마다
    // 기록을 검증해야 한다.

    /** 처음 올릴 때 받아 주는 최고 스테이지. 랭킹이 생기기 전부터 멀리 간 사람도 여기서 시작해 따라 올라온다. */
    const val RANK_FIRST_MAX = 10

    /** 한 스테이지 오르는 데 필요한 최소 시간. 실제로 한 스테이지를 깨는 데 이보다 오래 걸린다. */
    const val RANK_MS_PER_STAGE = 60_000L

    /** 한 번에 오를 수 있는 최대 칸 수. 오래 쉬었다 와도 한꺼번에 튀지 못하게. */
    const val RANK_MAX_JUMP = 20

    /** 올리기 사이 최소 간격. 연달아 두드리는 것을 막는다. */
    const val RANK_MIN_INTERVAL_MS = 5_000L

    const val RANK_STAGE_CAP = 1000

    /** 서버 시계와 폰 시계가 어긋나도 거부되지 않게 계산에서 빼 두는 여유. */
    private const val CLOCK_SLACK_MS = 2_000L

    /**
     * 지금 랭킹에 올릴 스테이지. 서버 규칙이 받아 줄 만큼만 올린다.
     *
     * @param localBest 이 기기의 모험 최고 스테이지
     * @param serverStage 서버에 올라가 있는 값(처음이면 null)
     * @param serverAt 그 값을 올린 서버 시각(처음이면 null)
     * @param serverNow 지금 서버 시각 추정치
     * @return 올릴 값. 올릴 필요가 없거나 아직 기다려야 하면 null
     */
    fun rankUploadStage(localBest: Int, serverStage: Int?, serverAt: Long?, serverNow: Long): Int? {
        val target = localBest.coerceIn(1, RANK_STAGE_CAP)
        if (serverStage == null || serverAt == null) return minOf(target, RANK_FIRST_MAX)
        if (target <= serverStage) return null
        val elapsed = serverNow - serverAt - CLOCK_SLACK_MS
        if (elapsed < RANK_MIN_INTERVAL_MS) return null
        val allowedJump = minOf(RANK_MAX_JUMP.toLong(), 1 + elapsed / RANK_MS_PER_STAGE).toInt()
        return minOf(target, serverStage + allowedJump)
    }
}
