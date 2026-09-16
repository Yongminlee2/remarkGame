package com.kkeutmal.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kkeutmal.game.databinding.ActivityOnlineLobbyBinding

/**
 * 친구 대전 로비 — 방 만들기와 코드로 들어가기.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets(includeIme = true)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvRecord.text =
            "친구 대전 전적  ${Wallet.onlineWins(this)}승 ${Wallet.onlineLosses(this)}패"

        // 첫 단어를 방장 폰이 고르므로 사전이 준비돼 있어야 한다
        WordDict.preload(this)

        Online.acquire(
            onReady = { id ->
                if (isFinishing || isDestroyed) return@acquire
                uid = id
                setStatus("")
                binding.btnCreate.isEnabled = true
                binding.btnJoin.isEnabled = true
            },
            onError = { msg -> setStatus(msg, error = true) }
        )

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
            binding.panelWaiting.visibility = View.GONE
            binding.panelChoose.visibility = View.VISIBLE
            setBusy(false)
            setStatus("방이 닫혔어요", error = true)
            return
        }

        // 방장: 친구가 들어왔으면 첫 단어를 골라 시작한다
        if (isHost && snap.state == "waiting" && snap.guest != null && !starting) {
            starting = true
            setStatus("친구가 들어왔어요! 시작하는 중…")
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
        val text = "끝말잇기 친구 대전 해요!\n코드: $code\n\n앱에서 「친구와 대전 → 코드로 들어가기」에 넣어 주세요."
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                "코드 보내기"
            )
        )
    }

    override fun onDestroy() {
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
