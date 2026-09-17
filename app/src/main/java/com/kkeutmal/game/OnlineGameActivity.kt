package com.kkeutmal.game

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kkeutmal.game.databinding.ActivityGameBinding

/**
 * 온라인 대전 화면. AI 대전 화면의 레이아웃을 그대로 쓰고 아이템·목표 줄만 감춘다.
 *
 * **서버가 판정하지 않는다.** 두 폰이 같은 방을 지켜보다가 [OnlineRules.judge] 로 같은 결론을
 * 내고, 먼저 선언한 쪽이 결과로 남는다(서버 규칙이 결과를 한 번만 받는다). 상대가 낸 단어도
 * 내 폰의 사전으로 다시 확인한다 — 조작한 앱이 아무 단어나 우기는 것을 막는다.
 */
class OnlineGameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_HOST = "host"
        private const val REQ_MIC = 72
        private const val TICK_MS = 200L

        /** 결과 선언이 서버에 안 닿았을 때 다시 보내기까지 기다리는 시간 */
        private const val REDECLARE_MS = 2_000L

        /**
         * 단어를 보낸 직후 내 시간 초과를 선언하지 않는 시간. 마감 직전에 보낸 단어가 서버를
         * 돌아 차례가 넘어가기 전에 "시간 초과" 를 스스로 선언하면 억울한 패배가 된다.
         */
        private const val SUBMIT_GRACE_MS = 3_000L

        /** 쓰는 중인 글자를 보내는 간격. 글자마다 보내면 서버 쓰기가 불필요하게 많아진다. */
        private const val TYPING_SEND_MS = 300L
    }

    private lateinit var binding: ActivityGameBinding
    private lateinit var room: OnlineRoom
    private val engine = GameEngine(AiLevel.NORMAL, noTimer = false)
    private val adapter = ChatAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private lateinit var voice: VoiceInput
    private var vibrator: Vibrator? = null

    private var isHost = false
    private var me: String? = null
    private var snap: RoomSnap? = null
    private var shownFirst = false
    private var applied = 0
    private var openedTurnAt = -1L
    private var finished = false
    private var roomDeleted = false
    private var lastDeclareAt = 0L
    private var submittedAt = 0L

    /** 상대 단어가 규칙에 안 맞아 이미 이겼다고 선언했다. 결과가 올 때까지 내 차례를 열지 않는다. */
    private var claimedWin = false

    /** 음성으로 낸 단어 수(미션용) */
    private var voiceWordCount = 0

    /** 힌트는 서버에 안 적으므로 한 판에 한 번을 여기서 센다 */
    private var hintUsed = false
    /** 아이템 요청이 서버를 도는 중(두 번 눌러 두 개가 빠지지 않게) */
    private var itemBusy = false
    private var activeHint: String? = null
    private var lastSentTyping = ""
    private val hideErrorRunnable = Runnable {
        val hint = activeHint
        if (hint != null) showHint(hint) else binding.tvError.visibility = View.GONE
    }
    private val sendTyping = Runnable {
        val uid = me ?: return@Runnable
        // 음성 입력은 글자를 넣자마자 제출한다 — 제출 뒤에 늦게 도착한 "쓰는 중" 을 다시 적지 않게
        if (finished || !binding.etWord.isEnabled || snap?.turn != uid) return@Runnable
        val text = binding.etWord.text?.toString().orEmpty()
        if (text == lastSentTyping) return@Runnable
        lastSentTyping = text
        room.setTyping(uid, text)
    }

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            if (!finished) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets(includeIme = true)
        Ads.attachBanner(this, binding.adContainer)

        val code = intent.getStringExtra(EXTRA_CODE)
        if (code == null) {
            finish()
            return
        }
        isHost = intent.getBooleanExtra(EXTRA_HOST, false)
        room = OnlineRoom(code)

        // 온라인 대전엔 목표·보스 규칙·점수가 없다. 아이템은 시간·힌트·패스만(2배는 보상이 없어 뺐다).
        listOf(binding.btnItemDouble, binding.bossBanner, binding.tvGoal, binding.tvScore)
            .forEach { it.visibility = View.GONE }
        binding.btnItemTime.setOnClickListener { useTimeItem() }
        binding.btnItemHint.setOnClickListener { useHintItem() }
        binding.btnItemPass.setOnClickListener { usePassItem() }
        refreshItemBar()
        binding.tvDifficulty.text = "온라인 대전"

        adapter.otherLabel = "상대"
        adapter.playerAvatarId = Wallet.selectedAvatarId(this)
        binding.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recycler.adapter = adapter

        audio = AudioManager(this)
        audio.preload()
        vibrator = ContextCompat.getSystemService(this, Vibrator::class.java)

        voice = VoiceInput(this)
        voice.onStateChange = { on ->
            binding.btnMic.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, if (on) R.color.mic_listening else R.color.accent2_dark)
            )
            binding.etWord.hint = if (on) "듣는 중… 말씀하세요!" else getString(R.string.input_hint)
        }
        voice.onResult = { word ->
            voiceWordCount++
            binding.etWord.setText(word)
            binding.etWord.setSelection(word.length)
            submit()
        }
        voice.onFail = { msg -> showError(msg) }
        binding.btnMic.setOnClickListener {
            when {
                voice.listening -> voice.cancel()
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> voice.start()
                else -> requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_MIC)
            }
        }

        binding.btnSend.setOnClickListener { submit() }
        binding.etWord.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!binding.etWord.isEnabled) return
                handler.removeCallbacks(sendTyping)
                handler.postDelayed(sendTyping, TYPING_SEND_MS)
            }
        })
        binding.etWord.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND) {
                submit(); true
            } else false
        }
        binding.btnSurrender.setOnClickListener { confirmLeave() }
        onBackPressedDispatcher.addCallback(this) { confirmLeave() }

        setInputEnabled(false)
        binding.tvRequired.text = "연결하는 중…"
        binding.tvRound.text = "라운드 1"
        binding.loadingOverlay.visibility = View.VISIBLE

        // 앱이 통째로 꺼지면 다음에 켤 때 패배로 센다(화면을 정상적으로 나가면 그 자리에서 센다)
        Wallet.markOnlineStarted(this)

        // 연결은 동기적으로 먼저 빌린다 — 로비가 닫히며 연결을 돌려줘도 끊기지 않게
        Online.acquire(
            onReady = { uid ->
                me = uid
                maybeBegin()
            },
            onError = { msg -> leaveWithoutResult(msg) }
        )
        WordDict.preload(this) { maybeBegin() }
    }

    /** 연결과 사전이 둘 다 준비되면 방을 지켜보기 시작한다. */
    private fun maybeBegin() {
        if (isFinishing || isDestroyed || finished) return
        val uid = me ?: return
        if (!WordDict.ready) return
        if (binding.loadingOverlay.visibility != View.VISIBLE) return // 이미 시작했다
        binding.loadingOverlay.visibility = View.GONE
        room.armPresence(uid)
        room.listen(::onRoom)
        handler.post(tick)
    }

    private fun onRoom(s: RoomSnap?) {
        if (finished || isFinishing || isDestroyed) return
        val uid = me ?: return
        if (s == null) {
            leaveWithoutResult("방이 사라졌어요")
            return
        }
        snap = s
        val opponent = s.opponentOf(uid)
        AvatarCatalog.byId(s.avatarOf(opponent) ?: "")?.let {
            binding.tvDifficulty.text = "vs ${it.name}"
        }

        val first = s.first
        if (!shownFirst && first != null) {
            shownFirst = true
            engine.applyWord(first)
            val meaning = WordDict.meaning(first)
            adapter.add(ChatItem.Sys("🎯 첫 단어는 「$first」" + if (meaning.isNullOrBlank()) "" else "\n$meaning"))
            adapter.add(
                ChatItem.Sys(if (s.turn == uid) "내가 먼저 이어요!" else "상대가 먼저 이어요")
            )
        }

        val appliedBefore = applied
        while (applied < s.moves.size) {
            val m = s.moves[applied]
            applied++
            if (m.pass) {
                // 패스는 단어가 없다. 마지막 단어가 그대로라 상대는 자기 단어 끝 글자로 다시 잇는다.
                adapter.add(
                    ChatItem.Sys(if (m.by == uid) "🔄 패스했어요" else "🔄 상대가 패스했어요! 다시 이어 주세요")
                )
                audio.play("sfx_ai")
            } else if (m.by == uid) {
                engine.applyWord(m.word)
                adapter.add(ChatItem.Player(m.word, 0, WordDict.meaning(m.word)))
                audio.play("sfx_ok")
            } else {
                // 상대가 낸 단어를 내 사전으로 다시 확인한다
                when (val v = engine.validate(m.word)) {
                    is GameEngine.Verdict.Bad -> {
                        adapter.add(ChatItem.Ai(m.word, null))
                        adapter.add(ChatItem.Sys("⚠️ 규칙에 맞지 않는 단어예요 — ${v.message}"))
                        if (s.winner == null) {
                            claimedWin = true
                            declare(uid, OnlineRules.Reason.INVALID, force = true)
                        }
                    }
                    is GameEngine.Verdict.Ok -> {
                        engine.applyWord(m.word)
                        adapter.add(ChatItem.Ai(m.word, WordDict.meaning(m.word)))
                        audio.play("sfx_ai")
                    }
                }
            }
        }
        // 두 사람이 한 번씩 이으면 한 라운드
        binding.tvRound.text = "라운드 ${s.moves.size / 2 + 1}"
        if (applied != appliedBefore || appliedBefore == 0) scrollToEnd()
        refreshItemBar()

        if (s.winner != null) {
            finishWithResult(s)
            return
        }
        if (claimedWin) return

        if (s.state == "playing" && s.turn == uid && s.turnAt > 0 && s.turnAt != openedTurnAt) {
            openedTurnAt = s.turnAt
            openMyTurn(opponent)
        } else if (s.turn != uid) {
            setInputEnabled(false)
            clearHint()
            val typing = opponent?.let { s.typing[it] }?.let { WordDict.dictPrefixOf(it) }
            if (typing.isNullOrEmpty()) {
                binding.tvRequired.text = "상대 차례예요…"
            } else {
                binding.tvRequired.text = "✏️ 상대가 쓰는 중: $typing"
                binding.tvRequired.alpha = 1f
            }
        }
    }

    // ---------- 아이템 ----------

    private fun myUsed(): Set<String> = me?.let { snap?.used?.get(it) }.orEmpty()

    private fun refreshItemBar() {
        val used = myUsed()
        fun show(btn: android.widget.TextView, id: String, emoji: String, spent: Boolean) {
            btn.text = "$emoji×${Wallet.itemCount(this, id)}"
            btn.alpha = if (spent) 0.35f else 1f
        }
        show(binding.btnItemTime, "item_time", "⏰", OnlineRules.Item.TIME in used)
        show(binding.btnItemHint, "item_hint", "💡", hintUsed)
        show(binding.btnItemPass, "item_pass", "🔄", OnlineRules.Item.PASS in used)
    }

    /** 아이템을 써도 되는 때인가. 안 되면 까닭을 보여 주고 false. */
    private fun canUseItem(id: String, emoji: String, spent: Boolean): Boolean {
        if (finished || itemBusy || !binding.etWord.isEnabled || snap?.turn != me) return false
        if (spent) {
            showError("$emoji 온라인 대전에서는 한 판에 한 번만 쓸 수 있어요")
            return false
        }
        if (Wallet.itemCount(this, id) <= 0) {
            showError("$emoji 아이템이 없어요. 상점에서 구할 수 있어요")
            return false
        }
        return true
    }

    private fun useTimeItem() {
        val uid = me ?: return
        if (!canUseItem("item_time", "⏰", OnlineRules.Item.TIME in myUsed())) return
        itemBusy = true
        room.useTime(uid) { ok ->
            itemBusy = false
            if (isFinishing || isDestroyed) return@useTime
            if (!ok) {
                showError("아이템을 쓰지 못했어요")
                return@useTime
            }
            // 서버가 받아 준 뒤에만 뺀다 — 실패했는데 아이템만 사라지면 억울하다
            Wallet.useItem(this, "item_time")
            audio.play("sfx_ok")
            refreshItemBar()
        }
    }

    private fun useHintItem() {
        if (!canUseItem("item_hint", "💡", hintUsed)) return
        val hints = engine.hintWords(3)
        if (hints.isEmpty()) {
            showError("힌트로 알려줄 단어가 없어요")
            return
        }
        hintUsed = true
        Wallet.useItem(this, "item_hint")
        audio.play("sfx_ok")
        activeHint = "💡 ${hints.joinToString("  ·  ")}"
        handler.removeCallbacks(hideErrorRunnable)
        showHint(activeHint!!)
        refreshItemBar()
    }

    private fun usePassItem() {
        val uid = me ?: return
        val opponent = snap?.opponentOf(uid) ?: return
        if (!canUseItem("item_pass", "🔄", OnlineRules.Item.PASS in myUsed())) return
        itemBusy = true
        setInputEnabled(false)
        submittedAt = Online.serverNow()
        room.pass(uid, opponent) { ok ->
            itemBusy = false
            if (isFinishing || isDestroyed) return@pass
            if (!ok) {
                submittedAt = 0L
                setInputEnabled(true)
                showError("패스하지 못했어요")
                return@pass
            }
            Wallet.useItem(this, "item_pass")
            binding.etWord.setText("")
            refreshItemBar()
        }
    }

    private fun showHint(text: String) {
        binding.tvError.setTextColor(ContextCompat.getColor(this, R.color.warn))
        binding.tvError.text = text
        binding.tvError.visibility = View.VISIBLE
    }

    private fun clearHint() {
        if (activeHint == null) return
        activeHint = null
        handler.removeCallbacks(hideErrorRunnable)
        binding.tvError.visibility = View.GONE
    }

    private fun openMyTurn(opponent: String?) {
        val starts = engine.allowedStarts()
        if (starts != null && !engine.hasAnyCandidate(starts)) {
            adapter.add(ChatItem.Sys("${engine.allowedStartsLabel()}(으)로 시작하는 단어가 사전에 없어요… 한방단어! 💥"))
            scrollToEnd()
            if (opponent != null) declare(opponent, OnlineRules.Reason.HANBANG, force = true)
            return
        }
        setInputEnabled(true)
        binding.tvRequired.text = "${engine.linkPrompt()} 하는 단어!"
        binding.etWord.requestFocus()
        vibrate(30)
    }

    private fun submit() {
        if (finished || !binding.etWord.isEnabled) return
        val uid = me ?: return
        val s = snap ?: return
        val opponent = s.opponentOf(uid) ?: return
        if (s.turn != uid) return
        val input = binding.etWord.text?.toString() ?: return
        when (val v = engine.validate(input)) {
            is GameEngine.Verdict.Bad -> {
                showError(v.message)
                audio.play("sfx_error")
                vibrate(80)
            }
            is GameEngine.Verdict.Ok -> {
                setInputEnabled(false)
                clearHint()
                binding.etWord.setText("")
                submittedAt = Online.serverNow()
                // 여기서 화면에 바로 올리지 않는다. 서버를 돌아온 단어만 올려야 두 폰의 순서가 같다.
                room.submit(uid, v.word, opponent) { msg ->
                    submittedAt = 0L
                    showError(msg)
                    setInputEnabled(true)
                }
            }
        }
    }

    private fun onTick() {
        if (finished) return
        val uid = me ?: return
        val s = snap ?: return
        if (s.state != "playing" || s.turnAt <= 0L) return
        val now = Online.serverNow()

        // 남은 시간 막대. 내 차례든 상대 차례든 같은 마감을 보여 준다.
        val leftMs = (OnlineRules.turnDeadline(s.turnAt, s.extra) - now).coerceAtLeast(0L)
        val totalMs = OnlineRules.TURN_SEC * 1000L + s.extra
        binding.timerBar.max = 1000
        binding.timerBar.progress = ((leftMs * 1000) / totalMs).toInt().coerceIn(0, 1000)
        binding.tvTimer.text = String.format("%.1f초", leftMs / 1000f)
        val frac = leftMs.toFloat() / totalMs
        val color = ContextCompat.getColor(
            this,
            when {
                frac < 0.15f -> R.color.error
                frac < 0.35f -> R.color.warn
                else -> R.color.timer_ok
            }
        )
        binding.timerBar.setIndicatorColor(color)
        binding.tvTimer.setTextColor(color)

        val opponent = s.opponentOf(uid) ?: return
        when (val c = OnlineRules.judge(uid, s.turn, s.turnAt, s.gone[opponent], s.winner != null, now, s.extra)) {
            is OnlineRules.Claim.Win -> declare(uid, c.reason)
            is OnlineRules.Claim.Lose -> {
                val justSubmitted = submittedAt > 0L && now - submittedAt < SUBMIT_GRACE_MS
                if (!justSubmitted) declare(opponent, c.reason)
            }
            OnlineRules.Claim.None -> Unit
        }
    }

    /** 결과를 서버에 적는다. 안 닿았을 수 있으니 조금 뒤 다시 보낼 수 있게 해 둔다. */
    private fun declare(winner: String, reason: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastDeclareAt < REDECLARE_MS) return
        lastDeclareAt = now
        setInputEnabled(false)
        room.declare(winner, reason)
    }

    private fun finishWithResult(s: RoomSnap) {
        if (finished) return
        finished = true
        handler.removeCallbacks(tick)
        setInputEnabled(false)
        val iWon = s.winner == me
        Wallet.recordOnlineResult(this, iWon)
        Wallet.clearOnlineInProgress(this)
        Missions.bump(this, Mission.PLAY_3, 1)
        Missions.bump(this, Mission.ONLINE_PLAY_1, 1)
        if (iWon) Missions.bump(this, Mission.ONLINE_WIN_1, 1)
        me?.let { uid ->
            val mine = s.moves.filter { it.by == uid && !it.pass }
            Missions.bump(this, Mission.LONG_WORD_3, mine.count { it.word.length >= 4 })
        }
        Missions.bump(this, Mission.VOICE_5, voiceWordCount)

        audio.play(if (iWon) "sfx_win" else "sfx_lose")
        if (iWon) vibrate(100, 50, 100, 50, 100) else vibrate(200, 100, 200)
        adapter.add(ChatItem.Sys(if (iWon) "🏆 이겼어요!" else "😢 졌어요"))
        scrollToEnd()
        binding.tvRequired.text = if (iWon) "승리!" else "패배"

        // 결과가 났으니 "끊기면 나갔다고 적기" 를 푼다. 안 풀면 방을 지운 뒤에 그 표시만 남는다.
        room.stopListening()
        room.keepOnDisconnect()
        // 조금 기다렸다 방을 지운다. 바로 지우면 상대 폰이 결과를 받기 전에 방이 사라진다.
        // 둘 다 지우러 간다 — 방장이 나가 버린 판은 손님이 치워야 방이 서버에 안 남는다.
        handler.postDelayed({ deleteRoomOnce() }, 5_000L)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (iWon) "🏆 승리!" else "😢 패배")
            .setMessage(
                OnlineRules.resultMessage(s.reason, iWon) +
                    "\n\n온라인 대전 전적  ${Wallet.onlineWins(this)}승 ${Wallet.onlineLosses(this)}패"
            )
            .setPositiveButton("나가기") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun deleteRoomOnce() {
        if (roomDeleted) return
        roomDeleted = true
        room.delete()
    }

    private fun confirmLeave() {
        if (finished) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("그만할까요?")
            .setMessage("지금 나가면 온라인 대전 패배로 기록돼요")
            .setPositiveButton("항복") { _, _ ->
                val uid = me
                val opponent = uid?.let { snap?.opponentOf(it) }
                if (opponent != null && snap?.state == "playing") {
                    declare(opponent, OnlineRules.Reason.SURRENDER, force = true)
                } else {
                    leaveWithoutResult(null)
                }
            }
            .setNegativeButton("계속", null)
            .show()
    }

    /** 결과 없이 나간다(연결 실패·방이 사라짐·시작 전). */
    private fun leaveWithoutResult(message: String?) {
        if (finished) return
        finished = true
        handler.removeCallbacks(tick)
        // 결과를 모르는 채로 끝났다(연결 실패·방이 사라짐). 누가 이겼는지 모르니 세지 않는다.
        Wallet.clearOnlineInProgress(this)
        if (message == null) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton("확인") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun setInputEnabled(enabled: Boolean) {
        binding.etWord.isEnabled = enabled
        binding.btnSend.isEnabled = enabled
        binding.btnSend.alpha = if (enabled) 1f else 0.4f
        binding.btnMic.isEnabled = enabled
        binding.btnMic.alpha = if (enabled) 1f else 0.4f
        binding.tvRequired.alpha = if (enabled) 1f else 0.45f
        if (!enabled && ::voice.isInitialized && voice.listening) voice.cancel()
    }

    private fun showError(message: String) {
        binding.tvError.setTextColor(ContextCompat.getColor(this, R.color.error))
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        handler.removeCallbacks(hideErrorRunnable)
        handler.postDelayed(hideErrorRunnable, 2200L)
        binding.inputBar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun scrollToEnd() {
        if (adapter.itemCount > 0) binding.recycler.smoothScrollToPosition(adapter.itemCount - 1)
    }

    private fun vibrate(vararg ms: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (ms.size == 1) v.vibrate(VibrationEffect.createOneShot(ms[0], VibrationEffect.DEFAULT_AMPLITUDE))
        else v.vibrate(VibrationEffect.createWaveform(ms, -1))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) voice.start()
            else showError("마이크 권한을 허용해야 음성 입력을 쓸 수 있어요")
        }
    }

    override fun onPause() {
        super.onPause()
        if (::voice.isInitialized && voice.listening) voice.cancel()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::room.isInitialized) {
            room.stopListening()
            if (finished) {
                // 결과가 난 방에 "나갔다" 표시가 남지 않게 푼다
                room.keepOnDisconnect()
                if (snap?.winner != null) deleteRoomOnce()
            }
            // 결과 없이 판 도중에 나가면 연결을 돌려주는 순간 서버가 "나갔다" 를 적고,
            // 상대 폰이 10초 뒤 승리를 선언한다. 내 쪽 전적은 여기서 바로 센다.
            if (!finished && snap?.state == "playing") {
                Wallet.recordOnlineResult(this, win = false)
                Wallet.clearOnlineInProgress(this)
            }
        }
        if (::audio.isInitialized) audio.cleanup()
        if (::voice.isInitialized) voice.cleanup()
        Online.release()
        super.onDestroy()
    }
}
