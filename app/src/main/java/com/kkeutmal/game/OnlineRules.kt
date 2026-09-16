package com.kkeutmal.game

import kotlin.random.Random

/**
 * 친구 대전·랭킹 규칙 중 **서버와 화면을 타지 않는 부분**.
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

    /** 친구 대전 한 차례 제한시간. 자유 대전 '보통' 과 같다. */
    const val TURN_SEC = 25

    /**
     * 상대의 시간 초과를 선언하기 전에 더 기다리는 여유.
     * 상대가 마감 직전에 낸 단어는 네트워크를 건너오느라 조금 늦게 도착한다.
     * **이기는 쪽이 선언하는 판정이라 관대해야 한다** — 박하게 잡으면 억울한 패배가 생긴다.
     */
    const val TURN_GRACE_MS = 3_000L

    /** 연결이 끊긴 뒤 패배로 치기까지 기다리는 시간. 지하철 같은 잠깐 끊김은 봐준다. */
    const val DISCONNECT_GRACE_MS = 10_000L

    fun turnDeadline(turnAt: Long): Long = turnAt + TURN_SEC * 1000L

    /** 화면에 보여 줄 남은 초. */
    fun secondsLeft(turnAt: Long, serverNow: Long): Int =
        ((turnDeadline(turnAt) - serverNow + 999) / 1000).toInt().coerceIn(0, TURN_SEC)

    /** 상대 차례가 여유까지 다 넘겼는가. 넘겼으면 내가 이겼다고 선언해도 된다. */
    fun opponentTimedOut(turnAt: Long, serverNow: Long): Boolean =
        serverNow > turnDeadline(turnAt) + TURN_GRACE_MS

    /** 상대 연결이 끊긴 지 여유보다 오래됐는가. goneSince 가 null 이면 연결돼 있다. */
    fun opponentAbandoned(goneSince: Long?, serverNow: Long): Boolean =
        goneSince != null && serverNow - goneSince > DISCONNECT_GRACE_MS

    // ---------- 랭킹 ----------
    //
    // 랭킹은 **모험 최고 스테이지**로 매긴다. 친구 대전 결과로 매기면 폰 두 대로 짜고 쳐서
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
