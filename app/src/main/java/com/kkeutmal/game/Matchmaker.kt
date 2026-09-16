package com.kkeutmal.game

import android.os.Handler
import android.os.Looper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

/**
 * 랜덤 매칭. 짝 짓는 순서는 [OnlineRules.pickOpponent] 의 설명 참고.
 *
 * 짝이 지어져 **두 사람이 같은 방에 들어간 순간** [onMatched] 를 한 번 부르고 손을 뗀다.
 * 그 뒤(첫 단어 고르기·시작)는 친구 대전과 똑같이 로비가 한다. 메인 스레드에서만 쓴다.
 */
class Matchmaker(
    private val uid: String,
    private val avatar: String,
    private val onMatched: (room: OnlineRoom, host: Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val queue = Online.db.getReference("queue")
    private val mine = queue.child(uid)
    private val handler = Handler(Looper.getMainLooper())

    private var active = false
    /** 찜하는 중이거나 찜당해 들어가는 중. 이때는 AI 로 넘어가지 않는다. */
    private var busy = false
    /** [giveUp] 이 대기열에서 빠지는 중 */
    private var stopping = false
    /** 화면을 떠났다. 늦게 도착한 콜백이 다시 매칭을 살리지 못하게 한다. */
    private var cancelled = false
    private var queued = false
    private var myAt: Long? = null
    private var mineListener: ValueEventListener? = null
    private var hosting: OnlineRoom? = null

    private val recheck = Runnable { search() }
    private val hostTimeout = Runnable { hosting?.let { hostFailed(it) } }

    fun start() {
        active = true
        search()
    }

    /**
     * 기다리다 AI 로 넘어갈 때. 대기열에서 조용히 빠진다.
     * @return false 면 지금 짝이 지어지는 중이라 못 빠진다 — 조금 뒤 다시 부른다.
     */
    fun giveUp(onStopped: () -> Unit): Boolean {
        if (busy) return false
        if (stopping) return true
        if (!active) {
            onStopped()
            return true
        }
        stopping = true
        active = false
        handler.removeCallbacksAndMessages(null)
        leaveQueue { claimedCode ->
            stopping = false
            if (cancelled) return@leaveQueue
            // 빠지려는 찰나에 누가 찜했다 — AI 대신 그 방으로 간다
            if (claimedCode != null) joinClaimed(claimedCode) else onStopped()
        }
        return true
    }

    /** 화면을 떠날 때. 결과를 기다리지 않고 전부 정리한다. */
    fun cancel() {
        cancelled = true
        active = false
        handler.removeCallbacksAndMessages(null)
        stopMine()
        if (queued) {
            mine.onDisconnect().cancel()
            mine.removeValue()
            queued = false
        }
        hosting?.let {
            it.stopListening()
            it.delete()
        }
        hosting = null
    }

    // ---------- 찾기 ----------

    private fun search() {
        if (!active || busy) return
        queue.orderByChild("at").limitToFirst(OnlineRules.QUEUE_SCAN).get()
            .addOnSuccessListener { s ->
                if (!active || busy) return@addOnSuccessListener
                val entries = s.children.mapNotNull { c ->
                    val at = c.child("at").getValue(Long::class.java)
                    if (c.key == null || at == null) null
                    else OnlineRules.QueueEntry(c.key!!, at, c.child("room").exists())
                }
                val target = OnlineRules.pickOpponent(entries, uid, myAt, Online.serverNow())
                if (target != null) {
                    claim(target)
                } else {
                    enqueue()
                    handler.postDelayed(recheck, OnlineRules.MATCH_RECHECK_MS)
                }
            }
            .addOnFailureListener { if (active) onError("인터넷 연결을 확인해 주세요") }
    }

    /** 대기열에 내 칸을 올리고, 누가 찜하는지 지켜본다. */
    private fun enqueue() {
        if (queued) return
        queued = true
        // 앱이 꺼지면 서버가 칸을 치운다. 안 치우면 아무도 안 오는 칸을 남들이 계속 찜한다.
        mine.onDisconnect().removeValue()
        mine.setValue(mapOf("avatar" to avatar, "at" to ServerValue.TIMESTAMP))
            .addOnFailureListener {
                queued = false
                if (active) onError("인터넷 연결을 확인해 주세요")
            }
        mineListener = mine.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (!s.exists()) return
                myAt = s.child("at").getValue(Long::class.java)
                val code = s.child("room").getValue(String::class.java) ?: return
                if (active && !busy) joinClaimed(code)
            }

            override fun onCancelled(e: DatabaseError) = Unit
        })
    }

    private fun stopMine() {
        mineListener?.let { mine.removeEventListener(it) }
        mineListener = null
    }

    /**
     * 내 칸을 내린다. 그새 누가 찜했으면 내리지 않고 그 방 코드를 돌려준다.
     * 트랜잭션이라 "찜" 과 "내리기" 가 엇갈려도 둘 중 하나만 일어난다.
     */
    private fun leaveQueue(onDone: (claimedCode: String?) -> Unit) {
        if (!queued) {
            onDone(null)
            return
        }
        mine.runTransaction(object : Transaction.Handler {
            override fun doTransaction(d: MutableData): Transaction.Result {
                if (d.child("room").value != null) return Transaction.abort()
                d.value = null
                return Transaction.success(d)
            }

            override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {
                val code = s?.child("room")?.getValue(String::class.java)
                if (!committed && code != null) {
                    onDone(code)
                    return
                }
                stopMine()
                mine.onDisconnect().cancel()
                queued = false
                myAt = null
                onDone(null)
            }
        })
    }

    // ---------- 내가 찜한다(방장) ----------

    private fun claim(target: String) {
        busy = true
        handler.removeCallbacks(recheck)
        leaveQueue { claimedCode ->
            if (claimedCode != null) {
                joinClaimed(claimedCode)
                return@leaveQueue
            }
            OnlineRoom.create(
                uid, avatar, invited = target,
                onCreated = { r ->
                    if (!active) {
                        r.delete()
                        return@create
                    }
                    r.deleteOnDisconnect()
                    queue.child(target).child("room").runTransaction(object : Transaction.Handler {
                        override fun doTransaction(d: MutableData): Transaction.Result {
                            if (d.value != null) return Transaction.abort()
                            d.value = r.code
                            return Transaction.success(d)
                        }

                        override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {
                            // 남이 먼저 찜했거나, 그 사람이 그새 빠졌다(서버가 거부)
                            if (!committed || e != null) {
                                r.delete()
                                retry()
                            } else {
                                waitForGuest(r)
                            }
                        }
                    })
                },
                onError = { retry() }
            )
        }
    }

    private fun waitForGuest(r: OnlineRoom) {
        if (!active) {
            r.delete()
            return
        }
        hosting = r
        handler.postDelayed(hostTimeout, OnlineRules.MATCH_JOIN_TIMEOUT_MS)
        r.listen { snap ->
            if (hosting !== r) return@listen
            if (snap?.guest == null) return@listen
            handler.removeCallbacks(hostTimeout)
            hosting = null
            r.stopListening()
            finish()
            onMatched(r, true)
        }
    }

    private fun hostFailed(r: OnlineRoom) {
        hosting = null
        r.stopListening()
        r.delete()
        retry()
    }

    // ---------- 누가 나를 찜했다(손님) ----------

    private fun joinClaimed(code: String) {
        if (cancelled) return
        busy = true
        active = true
        handler.removeCallbacksAndMessages(null)
        stopMine()
        mine.onDisconnect().cancel()
        mine.removeValue()
        queued = false
        myAt = null
        OnlineRoom.join(
            code, uid, avatar,
            onJoined = { r ->
                if (!active) return@join
                finish()
                onMatched(r, false)
            },
            onError = { retry() } // 방장이 그새 포기했다
        )
    }

    private fun retry() {
        busy = false
        search()
    }

    private fun finish() {
        active = false
        busy = false
        handler.removeCallbacksAndMessages(null)
    }
}
