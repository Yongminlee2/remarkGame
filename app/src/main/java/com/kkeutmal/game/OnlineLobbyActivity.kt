package com.kkeutmal.game

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kkeutmal.game.databinding.ActivityOnlineLobbyBinding

/**
 * 온라인 대전 로비 — 랜덤 매칭, 그리고 친구와 하는 방 만들기·코드로 들어가기.
 *
 * 들어오자마자 연결을 빌린다(대전하려고 들어온 화면이라). 나갈 때 반드시 돌려준다.
 */
class OnlineLobbyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineLobbyBinding
    private var uid: String? = null
    private var room: OnlineRoom? = null
    private var isHost = false

    /** 대전 화면으로 넘어갔는가. 넘어갔으면 방을 지우거나 닫지 않는다. */
    private var handedOff = false

    /** 방장 쪽에서 시작을 한 번만 걸기 위해 */
    private var starting = false

    private val handler = Handler(Looper.getMainLooper())
    private var matchmaker: Matchmaker? = null
    /** 랜덤 매칭 화면이 떠 있는가 */
    private var matching = false
    private var matchDeadline = 0L
    /** 지금 방이 랜덤 매칭으로 잡힌 방인가(시작 전에 닫히면 다시 찾는다) */
    private var randomRoom = false
    private var connectFailed = false
    private val matchTick = Runnable { onMatchTick() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets(includeIme = true)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvRecord.text =
            "온라인 대전 전적  ${Wallet.onlineWins(this)}승 ${Wallet.onlineLosses(this)}패"

        // 첫 단어를 방장 폰이 고르므로 사전이 준비돼 있어야 한다
        WordDict.preload(this)

        Online.acquire(
            onReady = { id ->
                if (isFinishing || isDestroyed) return@acquire
                uid = id
                setStatus("")
                binding.btnCreate.isEnabled = true
                binding.btnJoin.isEnabled = true
                if (matching) beginMatchmaker(id)
            },
            onError = { msg ->
                connectFailed = true
                if (matching) playAi("인터넷에 연결되지 않아 AI와 대전해요")
                else setStatus(msg, error = true)
            }
        )

        binding.btnRandom.setOnClickListener { startRandom() }
        binding.btnStopMatching.setOnClickListener { stopRandom() }
        binding.btnCreate.setOnClickListener { createRoom() }
        binding.btnJoin.setOnClickListener { joinRoom() }
        binding.btnCancel.setOnClickListener { closeWaitingRoom() }
        binding.btnShare.setOnClickListener { shareCode() }
    }

    private fun setStatus(text: String, error: Boolean = false) {
        binding.tvStatus.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        binding.tvStatus.text = text
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(this, if (error) R.color.error else R.color.text_dim)
        )
    }

    private fun setBusy(busy: Boolean) {
        binding.btnCreate.isEnabled = !busy && uid != null
        binding.btnJoin.isEnabled = !busy && uid != null
    }

    private fun myAvatar() = Wallet.selectedAvatarId(this)

    // ---------- 랜덤 매칭 ----------

    private fun startRandom() {
        if (connectFailed) {
            playAi("인터넷에 연결되지 않아 AI와 대전해요")
            return
        }
        matching = true
        setStatus("")
        binding.panelChoose.visibility = View.GONE
        binding.panelMatching.visibility = View.VISIBLE
        matchDeadline = SystemClock.elapsedRealtime() + OnlineRules.MATCH_WAIT_MS
        handler.removeCallbacks(matchTick)
        handler.post(matchTick)
        // 아직 접속 중이면 접속이 끝나는 대로 찾기 시작한다(onCreate 의 onReady)
        uid?.let { beginMatchmaker(it) }
    }

    private fun beginMatchmaker(me: String) {
        if (matchmaker != null) return
        matchmaker = Matchmaker(
            me, myAvatar(),
            onMatched = { r, host -> onRandomMatched(r, host) },
            onError = { if (matching) playAi("연결이 불안정해 AI와 대전해요") }
        ).also { it.start() }
    }

    private fun onMatchTick() {
        if (!matching || isFinishing || isDestroyed) return
        val left = matchDeadline - SystemClock.elapsedRealtime()
        if (left > 0) {
            binding.tvMatching.text = "상대를 찾는 중… ${(left + 999) / 1000}초"
            handler.postDelayed(matchTick, 250)
            return
        }
        binding.tvMatching.text = "상대가 없어서 AI를 부르는 중…"
        val mm = matchmaker ?: return playAi(null)
        val accepted = mm.giveUp { if (matching) playAi(null) }
        // 짝이 지어지는 중이면 기다려 준다. 그래도 너무 오래 걸리면(연결이 끊겼다) AI 로 간다.
        if (!accepted && -left > OnlineRules.MATCH_JOIN_TIMEOUT_MS + 5_000) return playAi(null)
        handler.postDelayed(matchTick, 1_000)
    }

    private fun onRandomMatched(r: OnlineRoom, host: Boolean) {
        if (!matching || isFinishing || isDestroyed) {
            if (host) r.delete()
            return
        }
        matching = false
        handler.removeCallbacks(matchTick)
        matchmaker = null
        room = r
        isHost = host
        randomRoom = true
        starting = false
        binding.tvMatching.text = "상대를 찾았어요!"
        r.listen(::onRoomChanged)
    }

    private fun stopRandom() {
        matching = false
        handler.removeCallbacks(matchTick)
        matchmaker?.cancel()
        matchmaker = null
        binding.panelMatching.visibility = View.GONE
        binding.panelChoose.visibility = View.VISIBLE
    }

    /** 상대를 못 찾았거나 연결이 안 될 때. 사람인 척하지 않고 AI 대전이라고 분명히 알린다. */
    private fun playAi(reason: String?) {
        if (isFinishing || isDestroyed) return
        stopRandom()
        Toast.makeText(this, reason ?: "상대를 못 찾아서 AI와 대전해요", Toast.LENGTH_LONG).show()
        val level = getSharedPreferences("kkeutmal", MODE_PRIVATE)
            .getString("sel_level", AiLevel.NORMAL.name)
        startActivity(Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_LEVEL, level))
        finish()
    }

    // ---------- 친구와 하기 ----------

    private fun createRoom() {
        val me = uid ?: return
        setBusy(true)
        setStatus("방을 만드는 중…")
        OnlineRoom.create(
            me, myAvatar(),
            onCreated = { r ->
                if (isFinishing || isDestroyed) {
                    r.delete()
                    return@create
                }
                room = r
                isHost = true
                r.deleteOnDisconnect()
                setStatus("")
                binding.panelChoose.visibility = View.GONE
                binding.panelWaiting.visibility = View.VISIBLE
                binding.tvCode.text = r.code
                r.listen(::onRoomChanged)
            },
            onError = { msg ->
                setBusy(false)
                setStatus(msg, error = true)
            }
        )
    }

    private fun joinRoom() {
        val me = uid ?: return
        val code = OnlineRules.normalizeCode(binding.etCode.text.toString())
        if (code == null) {
            setStatus("코드 6자리를 확인해 주세요", error = true)
            return
        }
        setBusy(true)
        setStatus("들어가는 중…")
        OnlineRoom.join(
            code, me, myAvatar(),
            onJoined = { r ->
                if (isFinishing || isDestroyed) return@join
                room = r
                isHost = false
                setStatus("친구가 시작하기를 기다리는 중…")
                r.listen(::onRoomChanged)
            },
            onError = { msg ->
                setBusy(false)
                setStatus(msg, error = true)
            }
        )
    }

    private fun onRoomChanged(snap: RoomSnap?) {
        if (handedOff || isFinishing || isDestroyed) return
        val r = room ?: return
        if (snap == null) {
            // 방장이 방을 닫았거나 방장 앱이 꺼졌다
            room = null
            r.stopListening()
            if (randomRoom) {
                // 시작 전에 상대가 나갔다 — 다시 찾는다
                randomRoom = false
                startRandom()
                return
            }
            binding.panelWaiting.visibility = View.GONE
            binding.panelChoose.visibility = View.VISIBLE
            setBusy(false)
            setStatus("방이 닫혔어요", error = true)
            return
        }

        // 방장: 친구가 들어왔으면 첫 단어를 골라 시작한다
        if (isHost && snap.state == "waiting" && snap.guest != null && !starting) {
            starting = true
            if (randomRoom) binding.tvMatching.text = "상대를 찾았어요! 시작하는 중…"
            else setStatus("친구가 들어왔어요! 시작하는 중…")
            WordDict.preload(this) {
                if (isFinishing || isDestroyed) return@preload
                val first = GameEngine(AiLevel.NORMAL, noTimer = false).openingWord()
                // 시작하면 방장이 잠깐 끊겨도 방이 사라지면 안 된다
                r.keepOnDisconnect()
                r.start(first, snap.guest) { msg -> setStatus(msg, error = true) }
            }
        }

        if (snap.state == "playing") handOff(r.code)
    }

    private fun handOff(code: String) {
        if (handedOff) return
        handedOff = true
        room?.stopListening()
        startActivity(
            Intent(this, OnlineGameActivity::class.java)
                .putExtra(OnlineGameActivity.EXTRA_CODE, code)
                .putExtra(OnlineGameActivity.EXTRA_HOST, isHost)
        )
        finish()
    }

    private fun closeWaitingRoom() {
        room?.let {
            it.stopListening()
            if (isHost) it.delete()
        }
        room = null
        binding.panelWaiting.visibility = View.GONE
        binding.panelChoose.visibility = View.VISIBLE
        setBusy(false)
        setStatus("")
    }

    private fun shareCode() {
        val code = room?.code ?: return
        val text = "끝말잇기 친구 대전 해요!\n코드: $code\n\n앱에서 「온라인 대전 → 코드로 들어가기」에 넣어 주세요."
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                "코드 보내기"
            )
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        matchmaker?.cancel()
        if (!handedOff) {
            room?.let {
                it.stopListening()
                if (isHost) it.delete()
            }
        }
        Online.release()
        super.onDestroy()
    }
}
