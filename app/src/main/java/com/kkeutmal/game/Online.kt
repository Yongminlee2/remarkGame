package com.kkeutmal.game

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Firebase 연결. **필요한 화면에서만 붙고, 아무도 안 쓰면 끊는다.**
 *
 * 무료 요금제는 동시접속 **100명**까지이고 올릴 수 없다. 앱을 켠 사람이 모두 연결을 쥐고 있으면
 * 친구 대전을 안 하는 사람까지 자리를 차지해 금방 찬다. 그래서 대전·랭킹 화면만 [acquire]/[release]
 * 로 연결을 빌려 쓴다.
 *
 * 사용 수를 세는 이유: 로비에서 대전 화면으로 넘어갈 때 **새 화면이 먼저 연결하고, 옛 화면이
 * 나중에 닫힌다.** 그냥 끊으면 옛 화면이 닫히면서 방금 시작한 대전의 연결을 끊어 버린다.
 * 메인 스레드에서만 부른다.
 */
object Online {

    /**
     * Realtime Database 주소. 싱가포르(asia-southeast1) — 이 제품은 서울을 지원하지 않는다.
     * 차례대로 주고받는 게임이라 지연은 체감되지 않는다.
     * google-services.json 을 데이터베이스보다 먼저 받아서 그 파일엔 주소가 없으므로 여기 적는다.
     */
    const val DB_URL = "https://wordchain-17a0d-default-rtdb.asia-southeast1.firebasedatabase.app"

    val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }

    @Volatile
    private var serverOffsetMs = 0L
    private var offsetListener: ValueEventListener? = null
    private var users = 0

    /** 서버 시각 추정치. 차례 마감은 폰 시계가 아니라 이것으로 잰다. */
    fun serverNow(): Long = System.currentTimeMillis() + serverOffsetMs

    fun myUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /** 연결을 빌린다. 익명 로그인까지 끝나면 [onReady]. 화면이 닫힐 때 반드시 [release]. */
    fun acquire(onReady: (uid: String) -> Unit, onError: (String) -> Unit) {
        users++
        db.goOnline()
        if (offsetListener == null) {
            offsetListener = db.getReference(".info/serverTimeOffset")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        serverOffsetMs = s.getValue(Long::class.java) ?: 0L
                    }

                    override fun onCancelled(e: DatabaseError) = Unit
                })
        }
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser
        if (current != null) {
            onReady(current.uid)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { r ->
                val uid = r.user?.uid
                if (uid != null) onReady(uid) else onError("접속하지 못했어요. 잠시 뒤 다시 해 주세요")
            }
            .addOnFailureListener { onError("인터넷 연결을 확인해 주세요") }
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        offsetListener?.let { db.getReference(".info/serverTimeOffset").removeEventListener(it) }
        offsetListener = null
        db.goOffline()
    }

}
