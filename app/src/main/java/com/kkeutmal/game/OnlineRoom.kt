package com.kkeutmal.game

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/** 방에 올라간 단어 하나. [pass] 면 단어 없이 차례만 넘긴 것(패스 아이템). */
data class OnlineMove(val by: String, val word: String, val pass: Boolean = false)

/** 방의 현재 모습. 서버에서 받은 그대로를 옮겨 담기만 한다 — 판정은 [OnlineRules]. */
data class RoomSnap(
    val code: String,
    val host: String?,
    val guest: String?,
    val hostAvatar: String?,
    val guestAvatar: String?,
    val createdAt: Long,
    val state: String?,
    val first: String?,
    val turn: String?,
    val turnAt: Long,
    val moves: List<OnlineMove>,
    val winner: String?,
    val reason: String?,
    val gone: Map<String, Long>,
    /** 이번 차례에 시간 아이템으로 늘린 시간(없으면 0) */
    val extra: Long = 0L,
    /** uid → 이번 판에 쓴 아이템 종류 */
    val used: Map<String, Set<String>> = emptyMap(),
    /** uid → 지금 입력창에 쓰고 있는 글자 */
    val typing: Map<String, String> = emptyMap()
) {
    fun opponentOf(uid: String): String? = if (uid == host) guest else host

    fun avatarOf(uid: String?): String? = when (uid) {
        null -> null
        host -> hostAvatar
        guest -> guestAvatar
        else -> null
    }

    companion object {
        fun from(code: String, s: DataSnapshot): RoomSnap? {
            if (!s.exists()) return null
            fun str(k: String) = s.child(k).getValue(String::class.java)
            fun long(k: String) = s.child(k).getValue(Long::class.java) ?: 0L
            // 서버가 찍은 시각(at) 순서로 놓는다. push 키도 대개 같은 순서지만 폰 시계로 만든 키라
            // 믿지 않는다 — 순서가 뒤집히면 이미 낸 단어를 다시 검사해 "규칙 위반" 으로 판정한다.
            val moves = s.child("moves").children
                .sortedBy { it.child("at").getValue(Long::class.java) ?: Long.MAX_VALUE }
                .mapNotNull { m ->
                val by = m.child("by").getValue(String::class.java)
                val word = m.child("word").getValue(String::class.java)
                val pass = m.child("pass").getValue(Boolean::class.java) == true
                when {
                    by == null -> null
                    pass -> OnlineMove(by, "", pass = true)
                    word != null -> OnlineMove(by, word)
                    else -> null
                }
            }
            val gone = s.child("gone").children.mapNotNull { g ->
                val at = g.getValue(Long::class.java)
                if (g.key != null && at != null) g.key!! to at else null
            }.toMap()
            return RoomSnap(
                code = code,
                host = str("host"),
                guest = str("guest"),
                hostAvatar = str("hostAvatar"),
                guestAvatar = str("guestAvatar"),
                createdAt = long("createdAt"),
                state = str("state"),
                first = str("first"),
                turn = str("turn"),
                turnAt = long("turnAt"),
                moves = moves,
                winner = s.child("result").child("winner").getValue(String::class.java),
                reason = s.child("result").child("reason").getValue(String::class.java),
                gone = gone,
                extra = long("extra"),
                used = s.child("used").children.mapNotNull { u ->
                    u.key?.let { k -> k to u.children.mapNotNull { it.key }.toSet() }
                }.toMap(),
                typing = s.child("typing").children.mapNotNull { t ->
                    val text = t.getValue(String::class.java)
                    if (t.key != null && !text.isNullOrEmpty()) t.key!! to text else null
                }.toMap()
            )
        }
    }
}

/**
 * 방 하나에 대한 읽기·쓰기. 무엇이 허용되는지는 `firebase/database.rules.json` 이 정한다 —
 * 여기서 규칙에 어긋나게 쓰면 서버가 거부하고 실패 콜백이 온다.
 */
class OnlineRoom(val code: String) {

    private val ref = Online.db.getReference("rooms").child(code)
    private var listener: ValueEventListener? = null
    private var connectedListener: ValueEventListener? = null

    companion object {
        /**
         * 방을 만든다. 코드가 이미 쓰이고 있으면 서버가 거부하므로 새 코드로 몇 번 더 시도한다.
         * (코드 조합이 8억 개가 넘어 겹칠 일은 거의 없다.)
         */
        fun create(
            uid: String,
            avatar: String,
            onCreated: (OnlineRoom) -> Unit,
            onError: (String) -> Unit,
            invited: String? = null,
            triesLeft: Int = 5
        ) {
            val room = OnlineRoom(OnlineRules.newRoomCode())
            val data = mutableMapOf<String, Any>(
                "host" to uid,
                "hostAvatar" to avatar,
                "state" to "waiting",
                "createdAt" to ServerValue.TIMESTAMP
            )
            // 랜덤 매칭 방은 찜한 사람만 들어올 수 있다. 대기열의 코드를 엿본 사람이 끼어들지 못하게.
            if (invited != null) data["invited"] = invited
            room.ref.setValue(data)
                .addOnSuccessListener { onCreated(room) }
                .addOnFailureListener {
                    if (triesLeft > 1) create(uid, avatar, onCreated, onError, invited, triesLeft - 1)
                    else onError("방을 만들지 못했어요. 인터넷 연결을 확인해 주세요")
                }
        }

        fun join(
            code: String,
            uid: String,
            avatar: String,
            onJoined: (OnlineRoom) -> Unit,
            onError: (String) -> Unit
        ) {
            val room = OnlineRoom(code)
            room.ref.get()
                .addOnSuccessListener { s ->
                    val snap = RoomSnap.from(code, s)
                    val problem = when {
                        snap == null -> "없는 코드예요. 다시 확인해 주세요"
                        snap.host == uid -> "내가 만든 방이에요. 친구에게 코드를 알려 주세요"
                        snap.state != "waiting" || snap.guest != null -> "이미 시작한 방이에요"
                        OnlineRules.roomExpired(snap.createdAt, Online.serverNow()) ->
                            "시간이 지나 닫힌 방이에요. 새로 만들어 달라고 해 주세요"
                        else -> null
                    }
                    if (problem != null) {
                        onError(problem)
                        return@addOnSuccessListener
                    }
                    room.ref.updateChildren(mapOf("guest" to uid, "guestAvatar" to avatar))
                        .addOnSuccessListener { onJoined(room) }
                        .addOnFailureListener { onError("들어갈 수 없는 방이에요") }
                }
                .addOnFailureListener { onError("인터넷 연결을 확인해 주세요") }
        }
    }

    fun listen(onChange: (RoomSnap?) -> Unit) {
        stopListening()
        listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) = onChange(RoomSnap.from(code, s))
            override fun onCancelled(e: DatabaseError) = onChange(null)
        })
    }

    fun stopListening() {
        listener?.let { ref.removeEventListener(it) }
        listener = null
        connectedListener?.let { Online.db.getReference(".info/connected").removeEventListener(it) }
        connectedListener = null
    }

    /**
     * 내가 끊기면 서버가 대신 "나갔다" 고 적게 한다. 다시 붙으면 그 표시를 지운다.
     *
     * 연결이 돌아올 때마다 다시 걸어야 한다 — 서버에 맡긴 "끊기면 할 일" 은 한 번 실행되면 사라진다.
     */
    fun armPresence(uid: String) {
        val mark = ref.child("gone").child(uid)
        connectedListener = Online.db.getReference(".info/connected")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (s.getValue(Boolean::class.java) != true) return
                    mark.onDisconnect().setValue(ServerValue.TIMESTAMP)
                    mark.removeValue()
                }

                override fun onCancelled(e: DatabaseError) = Unit
            })
    }

    /** 방장이 시작한다. 첫 단어는 방장이 고르고 손님이 먼저 받는다. */
    fun start(firstWord: String, guestUid: String, onError: (String) -> Unit) {
        ref.updateChildren(
            mapOf(
                "state" to "playing",
                "first" to firstWord,
                "turn" to guestUid,
                "turnAt" to ServerValue.TIMESTAMP
            )
        ).addOnFailureListener { onError("시작하지 못했어요") }
    }

    /** 단어를 내고 차례를 넘긴다. 한 번에 써야 서버가 "자기 차례에만 낸다" 를 확인할 수 있다. */
    fun submit(uid: String, word: String, opponentUid: String, onError: (String) -> Unit) {
        val key = ref.child("moves").push().key ?: return onError("보내지 못했어요")
        ref.updateChildren(
            mapOf(
                "moves/$key" to mapOf("by" to uid, "word" to word, "at" to ServerValue.TIMESTAMP),
                "turn" to opponentUid,
                "turnAt" to ServerValue.TIMESTAMP,
                // 늘린 시간은 그 차례에만 — 차례를 넘기며 같이 지운다
                "extra" to null,
                "typing/$uid" to null
            )
        ).addOnFailureListener { onError("보내지 못했어요. 인터넷 연결을 확인해 주세요") }
    }

    /** 시간 아이템. "썼다" 표시와 늘린 시간을 한 번에 적어야 서버가 한 판에 한 번을 지킬 수 있다. */
    fun useTime(uid: String, onDone: (ok: Boolean) -> Unit) {
        ref.updateChildren(
            mapOf("used/$uid/${OnlineRules.Item.TIME}" to true, "extra" to OnlineRules.TIME_ITEM_MS)
        ).addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /** 패스 아이템. 단어 없이 차례를 넘긴다 — 상대가 자기 단어 끝 글자로 다시 이어야 한다. */
    fun pass(uid: String, opponentUid: String, onDone: (ok: Boolean) -> Unit) {
        val key = ref.child("moves").push().key ?: return onDone(false)
        ref.updateChildren(
            mapOf(
                "moves/$key" to mapOf("by" to uid, "pass" to true, "at" to ServerValue.TIMESTAMP),
                "used/$uid/${OnlineRules.Item.PASS}" to true,
                "turn" to opponentUid,
                "turnAt" to ServerValue.TIMESTAMP,
                "extra" to null,
                "typing/$uid" to null
            )
        ).addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /** 입력창에 쓰는 중인 글자를 상대에게 보여 준다. 빈 글자는 지운다. */
    fun setTyping(uid: String, text: String) {
        val t = text.take(OnlineRules.TYPING_MAX)
        ref.child("typing").child(uid).setValue(t.ifEmpty { null })
    }

    /**
     * 결과를 적는다. 서버 규칙상 **한 번만** 적힌다 — 두 폰이 동시에 선언해도 먼저 도착한 것이 남는다.
     */
    fun declare(winner: String, reason: String) {
        ref.updateChildren(
            mapOf(
                "result" to mapOf("winner" to winner, "reason" to reason, "at" to ServerValue.TIMESTAMP),
                "state" to "done"
            )
        )
    }

    /**
     * 대기 중에 방장이 앱을 꺼 버리면 방도 서버가 지우게 한다. 안 그러면 아무도 못 들어오는
     * 방이 남는다. **시작하기 직전에 [keepOnDisconnect] 로 반드시 풀어야 한다** — 안 풀면
     * 게임 도중 방장이 잠깐만 끊겨도 방이 통째로 사라진다.
     */
    fun deleteOnDisconnect() {
        ref.onDisconnect().removeValue()
    }

    /** 이 방에 걸어 둔 "끊기면 할 일" 을 전부 푼다(아래 칸의 것까지). */
    fun keepOnDisconnect() {
        ref.onDisconnect().cancel()
    }

    /** 방을 지운다. 방장은 언제든, 손님은 결과가 난 뒤에만(서버 규칙). */
    fun delete() {
        keepOnDisconnect()
        ref.removeValue()
    }
}

/**
 * 랭킹 읽기·쓰기. 모험 최고 스테이지로 매긴다(이유는 [OnlineRules] 의 랭킹 절).
 *
 * **랭킹 화면을 한 번이라도 연 사람만** 올린다. 모험을 하는 모든 사람의 기록을 말없이 서버에
 * 올리면, 순위표를 본 적도 없는 사람이 공개 목록에 오른다.
 */
object Ranking {

    data class Entry(val uid: String, val avatar: String, val stage: Int)

    private fun ref() = Online.db.getReference("ranks")

    /**
     * 내 최고 스테이지를 서버 규칙이 받아 주는 만큼 올린다.
     * @param onDone 서버에 남은 내 스테이지(없으면 null)
     */
    fun syncMine(uid: String, localBest: Int, avatar: String, onDone: (Int?) -> Unit) {
        val mine = ref().child(uid)
        mine.get().addOnSuccessListener { s ->
            val serverStage = s.child("stage").getValue(Long::class.java)?.toInt()
            val serverAt = s.child("at").getValue(Long::class.java)
            val serverAvatar = s.child("avatar").getValue(String::class.java)
            val now = Online.serverNow()
            // 한 스테이지도 깨기 전에는 올리지 않는다 — 1스테이지가 줄줄이 늘어서기만 한다
            val up = if (localBest < 2) null
            else OnlineRules.rankUploadStage(localBest, serverStage, serverAt, now)
            // 스테이지는 그대로인데 아바타만 바꾼 경우도 반영한다
            val avatarOnly = up == null && serverStage != null && serverAvatar != avatar &&
                serverAt != null && now - serverAt >= OnlineRules.RANK_MIN_INTERVAL_MS + 2_000
            val stageToWrite = up ?: if (avatarOnly) serverStage else null
            if (stageToWrite == null) {
                onDone(serverStage)
                return@addOnSuccessListener
            }
            mine.setValue(mapOf("avatar" to avatar, "stage" to stageToWrite, "at" to ServerValue.TIMESTAMP))
                .addOnSuccessListener { onDone(stageToWrite) }
                .addOnFailureListener { onDone(serverStage) }
        }.addOnFailureListener { onDone(null) }
    }

    fun top(limit: Int, onResult: (List<Entry>) -> Unit, onError: (String) -> Unit) {
        ref().orderByChild("stage").limitToLast(limit).get()
            .addOnSuccessListener { s ->
                val list = s.children.mapNotNull { c ->
                    val avatar = c.child("avatar").getValue(String::class.java)
                    val stage = c.child("stage").getValue(Long::class.java)?.toInt()
                    if (c.key != null && avatar != null && stage != null) Entry(c.key!!, avatar, stage) else null
                }.sortedByDescending { it.stage }
                onResult(list)
            }
            .addOnFailureListener { onError("순위를 불러오지 못했어요. 인터넷 연결을 확인해 주세요") }
    }

    /** 내 기록을 순위표에서 내린다. */
    fun removeMine(uid: String, onDone: () -> Unit) {
        ref().child(uid).removeValue().addOnCompleteListener { onDone() }
    }
}
