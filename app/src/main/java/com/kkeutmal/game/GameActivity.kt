package com.kkeutmal.game

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kkeutmal.game.databinding.ActivityGameBinding
import com.kkeutmal.game.databinding.DialogResultBinding
import kotlin.random.Random

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LEVEL = "level"
        const val EXTRA_NO_TIMER = "no_timer"
        const val EXTRA_MODE = "mode"
        const val EXTRA_STAGE = "stage"
        private const val REQ_MIC = 71
    }

    private lateinit var binding: ActivityGameBinding
    private lateinit var engine: GameEngine
    private val adapter = ChatAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private val rng = Random(System.nanoTime())
    private lateinit var audio: AudioManager
    private var vibrator: Vibrator? = null
    private lateinit var voice: VoiceInput

    private var mode = GameMode.FREE
    private var stageNumber = 0
    private var stageConfig: StageConfig? = null
    private var voiceWordCount = 0
    private var longWordCount = 0
    private var doubleRewardActive = false

    private var timer: CountDownTimer? = null
    private var remainingMs = 0L
    private var secondsLeft = 0
    private var gameOver = false
    private val hideErrorRunnable = Runnable { binding.tvError.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets(includeIme = true)

        mode = runCatching { GameMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "FREE") }
            .getOrDefault(GameMode.FREE)

        if (mode == GameMode.ADVENTURE) {
            stageNumber = intent.getIntExtra(EXTRA_STAGE, 1)
            val cfg = Stage.configFor(stageNumber)
            stageConfig = cfg
            engine = GameEngine(cfg.aiLevel, noTimer = false, bossRules = cfg.boss?.rules ?: emptyList())
            binding.tvDifficulty.text = "${stageNumber}스테이지"
            binding.tvGoal.visibility = View.VISIBLE
            binding.tvGoal.text = "목표 ${cfg.targetRounds}라운드"
            cfg.boss?.let { boss ->
                binding.bossBanner.visibility = View.VISIBLE
                val ruleText = boss.rules.rejectionMessage()
                binding.bossBanner.text =
                    if (ruleText.isEmpty()) "👹 ${boss.name}" else "👹 ${boss.name} — $ruleText"
            }
        } else {
            val level = runCatching {
                AiLevel.valueOf(intent.getStringExtra(EXTRA_LEVEL) ?: AiLevel.NORMAL.name)
            }.getOrDefault(AiLevel.NORMAL)
            val noTimer = intent.getBooleanExtra(EXTRA_NO_TIMER, false)
            engine = GameEngine(level, noTimer)
            binding.tvDifficulty.text = level.label + if (noTimer) " ∞" else ""
        }

        audio = AudioManager(this)
        audio.preload()
        audio.startBgm()
        vibrator = ContextCompat.getSystemService(this, Vibrator::class.java)

        adapter.playerAvatarId = Wallet.selectedAvatarId(this)
        binding.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recycler.adapter = adapter

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
            if (gameOver) return@setOnClickListener
            when {
                voice.listening -> voice.cancel()
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED -> voice.start()
                else -> requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_MIC)
            }
        }

        binding.btnSend.setOnClickListener { submit() }
        binding.etWord.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND) {
                submit(); true
            } else false
        }
        binding.btnSurrender.setOnClickListener { confirmSurrender() }
        onBackPressedDispatcher.addCallback(this) {
            if (gameOver) finish() else confirmSurrender()
        }

        binding.btnItemTime.setOnClickListener { useTimeItem() }
        binding.btnItemHint.setOnClickListener { useHintItem() }
        binding.btnItemPass.setOnClickListener { usePassItem() }
        binding.btnItemDouble.setOnClickListener { useDoubleItem() }
        if (engine.noTimer) binding.btnItemTime.visibility = View.GONE
        refreshItemBar()

        setInputEnabled(false)
        updateHeader()

        if (WordDict.ready) {
            startGame()
        } else {
            binding.loadingOverlay.visibility = View.VISIBLE
            WordDict.preload(this) {
                if (isDestroyed || isFinishing) return@preload
                binding.loadingOverlay.visibility = View.GONE
                startGame()
            }
        }
    }

    private fun startGame() {
        adapter.add(ChatItem.Sys("첫 단어는 AI가 시작해요. 끝 글자를 이어보세요!"))
        aiThink {
            val word = engine.openingWord()
            engine.applyWord(word)
            adapter.add(ChatItem.Ai(word, WordDict.meaning(word)))
            scrollToEnd()
            beginPlayerTurn()
        }
    }

    private fun aiThink(block: () -> Unit) {
        adapter.add(ChatItem.Typing)
        scrollToEnd()
        audio.play("sfx_ai")
        handler.postDelayed({
            if (gameOver) return@postDelayed
            adapter.removeLastIfTyping()
            block()
        }, 650L + rng.nextLong(750L))
    }

    private fun beginPlayerTurn() {
        binding.tvRequired.text = "${engine.allowedStartsLabel()} (으)로 시작하는 단어!"
        val starts = engine.allowedStarts()
        if (starts != null && !engine.hasAnyCandidate(starts)) {
            adapter.add(ChatItem.Sys("${engine.allowedStartsLabel()}(으)로 시작하는 단어가 사전에 없어요… 한방단어! 💥"))
            scrollToEnd()
            endGame(win = false, reason = "한방단어에 당했어요")
            return
        }
        setInputEnabled(true)
        binding.etWord.requestFocus()
        startTimer((stageConfig?.timerSec ?: engine.timerSec) * 1000L)
    }

    private fun submit() {
        if (gameOver || !binding.etWord.isEnabled) return
        val input = binding.etWord.text?.toString() ?: return
        when (val verdict = engine.validate(input)) {
            is GameEngine.Verdict.Bad -> {
                showError(verdict.message)
                audio.play("sfx_error")
                vibrate(80)
            }
            is GameEngine.Verdict.Ok -> {
                stopTimer()
                setInputEnabled(false)
                binding.etWord.setText("")
                audio.play("sfx_ok")
                vibrate(40)
                val points = engine.acceptPlayer(verdict.word, secondsLeft)
                adapter.add(ChatItem.Player(verdict.word, points, WordDict.meaning(verdict.word)))
                scrollToEnd()
                updateHeader()
                if (verdict.word.length >= 4) longWordCount++
                val cfg = stageConfig
                if (cfg != null && engine.round >= cfg.targetRounds) {
                    adapter.add(ChatItem.Sys("🎉 ${cfg.targetRounds}라운드 달성! 스테이지 클리어"))
                    scrollToEnd()
                    endGame(win = true, reason = "${stageNumber}스테이지 클리어")
                    return
                }
                aiTurn()
            }
        }
    }

    private fun aiTurn() {
        aiThink {
            val word = engine.aiMove()
            if (word == null) {
                adapter.add(ChatItem.Sys("AI가 이을 단어를 찾지 못했어요! 🏳"))
                scrollToEnd()
                endGame(win = true, reason = "AI가 항복했어요")
            } else {
                engine.applyWord(word)
                adapter.add(ChatItem.Ai(word, WordDict.meaning(word)))
                scrollToEnd()
                beginPlayerTurn()
            }
        }
    }

    // ---------- 아이템 ----------

    private fun refreshItemBar() {
        fun label(id: String, emoji: String): String {
            val c = Wallet.itemCount(this, id)
            return "$emoji×$c"
        }
        binding.btnItemTime.text = label("item_time", "⏰")
        binding.btnItemHint.text = label("item_hint", "💡")
        binding.btnItemPass.text = label("item_pass", "🔄")
        binding.btnItemDouble.text = label("item_double", "🎯")
    }

    private fun playerTurnActive() = !gameOver && binding.etWord.isEnabled

    private fun useTimeItem() {
        if (!playerTurnActive() || engine.noTimer) return
        if (Wallet.itemCount(this, "item_time") <= 0) { showError("⏰ 아이템이 없어요. 상점에서 구매하세요"); return }
        Wallet.useItem(this, "item_time")
        startTimer(remainingMs + 15_000L)
        audio.play("sfx_ok")
        showInfo("⏰ +15초!")
        refreshItemBar()
    }

    private fun useHintItem() {
        if (!playerTurnActive()) return
        if (Wallet.itemCount(this, "item_hint") <= 0) { showError("💡 아이템이 없어요. 상점에서 구매하세요"); return }
        val hint = engine.hintWord()
        if (hint == null) { showError("힌트로 알려줄 단어가 없어요"); return }
        Wallet.useItem(this, "item_hint")
        audio.play("sfx_ok")
        showInfo("💡 힌트: $hint")
        refreshItemBar()
    }

    private fun usePassItem() {
        if (!playerTurnActive()) return
        if (Wallet.itemCount(this, "item_pass") <= 0) { showError("🔄 아이템이 없어요. 상점에서 구매하세요"); return }
        val newWord = engine.rerollAiWord()
        if (newWord == null) { showError("AI가 바꿀 단어를 찾지 못했어요"); return }
        Wallet.useItem(this, "item_pass")
        audio.play("sfx_ai")
        adapter.add(ChatItem.Sys("🔄 AI의 단어를 바꿨어요"))
        adapter.add(ChatItem.Ai(newWord, WordDict.meaning(newWord)))
        scrollToEnd()
        binding.tvRequired.text = "${engine.allowedStartsLabel()} (으)로 시작하는 단어!"
        refreshItemBar()
        // 새 단어가 한방단어인지 재확인
        val starts = engine.allowedStarts()
        if (starts != null && !engine.hasAnyCandidate(starts)) {
            adapter.add(ChatItem.Sys("바뀐 단어가 한방단어예요… 💥"))
            scrollToEnd()
            endGame(win = false, reason = "한방단어에 당했어요")
        }
    }

    private fun useDoubleItem() {
        if (!playerTurnActive()) return
        if (doubleRewardActive) { showInfo("🎯 이미 적용 중이에요"); return }
        if (Wallet.itemCount(this, "item_double") <= 0) {
            showError("🎯 아이템이 없어요. 상점에서 구매하세요"); return
        }
        Wallet.useItem(this, "item_double")
        doubleRewardActive = true
        audio.play("sfx_ok")
        showInfo("🎯 이번 판 보상 2배!")
        refreshItemBar()
    }

    // ---------- 타이머 ----------

    private fun startTimer(ms: Long) {
        stopTimer()
        if (engine.noTimer) {
            binding.timerBar.progress = 1000
            binding.tvTimer.text = "∞"
            binding.tvTimer.setTextColor(ContextCompat.getColor(this, R.color.timer_ok))
            return
        }
        val totalMs = (stageConfig?.timerSec ?: engine.timerSec) * 1000L
        binding.timerBar.max = 1000
        timer = object : CountDownTimer(ms, 100L) {
            override fun onTick(left: Long) {
                remainingMs = left
                secondsLeft = (left / 1000L).toInt()
                binding.timerBar.progress = ((left * 1000) / totalMs).toInt().coerceAtMost(1000)
                binding.tvTimer.text = String.format("%.1f초", left / 1000f)
                val frac = left.toFloat() / totalMs
                val color = when {
                    frac < 0.15f -> R.color.error
                    frac < 0.35f -> R.color.warn
                    else -> R.color.timer_ok
                }
                val c = ContextCompat.getColor(this@GameActivity, color)
                binding.timerBar.setIndicatorColor(c)
                binding.tvTimer.setTextColor(c)
                if (frac < 0.15f && left % 1000 < 100) audio.play("sfx_tick")
            }

            override fun onFinish() {
                binding.timerBar.progress = 0
                binding.tvTimer.text = "0.0초"
                audio.play("sfx_lose")
                vibrate(200, 100, 200)
                adapter.add(ChatItem.Sys("시간 초과! ⏰"))
                scrollToEnd()
                endGame(win = false, reason = "시간을 초과했어요")
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    // ---------- 상태/UI ----------

    @SuppressLint("SetTextI18n")
    private fun updateHeader() {
        binding.tvRound.text = "라운드 ${engine.round + 1}"
        binding.tvScore.text = "${engine.score}점"
    }

    private fun setInputEnabled(enabled: Boolean) {
        binding.etWord.isEnabled = enabled
        binding.btnSend.isEnabled = enabled
        binding.btnSend.alpha = if (enabled) 1f else 0.4f
        binding.btnMic.isEnabled = enabled
        binding.btnMic.alpha = if (enabled) 1f else 0.4f
        val itemAlpha = if (enabled) 1f else 0.4f
        binding.btnItemTime.isEnabled = enabled
        binding.btnItemTime.alpha = itemAlpha
        binding.btnItemHint.isEnabled = enabled
        binding.btnItemHint.alpha = itemAlpha
        binding.btnItemPass.isEnabled = enabled
        binding.btnItemPass.alpha = itemAlpha
        binding.btnItemDouble.isEnabled = enabled
        binding.btnItemDouble.alpha = itemAlpha
        binding.tvRequired.alpha = if (enabled) 1f else 0.45f
        if (!enabled && voice.listening) voice.cancel()
    }

    private fun showError(message: String) {
        binding.tvError.setTextColor(ContextCompat.getColor(this, R.color.error))
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        handler.removeCallbacks(hideErrorRunnable)
        handler.postDelayed(hideErrorRunnable, 2200L)
        binding.inputBar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun showInfo(message: String) {
        binding.tvError.setTextColor(ContextCompat.getColor(this, R.color.accent2))
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        handler.removeCallbacks(hideErrorRunnable)
        handler.postDelayed(hideErrorRunnable, 5000L)
    }

    private fun scrollToEnd() {
        if (adapter.itemCount > 0) binding.recycler.smoothScrollToPosition(adapter.itemCount - 1)
    }

    private fun confirmSurrender() {
        if (gameOver) return
        MaterialAlertDialogBuilder(this)
            .setTitle("항복할까요?")
            .setMessage("지금 항복하면 패배로 기록돼요.")
            .setPositiveButton("항복") { _, _ ->
                adapter.add(ChatItem.Sys("항복했어요 🏳"))
                scrollToEnd()
                endGame(win = false, reason = "항복했어요")
            }
            .setNegativeButton("계속하기", null)
            .show()
    }

    // ---------- 종료 ----------

    private fun endGame(win: Boolean, reason: String) {
        if (gameOver) return
        if (!win && !reason.contains("한방단어") && Wallet.itemCount(this, "item_revive") > 0) {
            stopTimer()
            MaterialAlertDialogBuilder(this)
                .setTitle("🛡 부활할까요?")
                .setMessage("부활 아이템을 써서 이어서 할 수 있어요 (보유 ${Wallet.itemCount(this, "item_revive")}개)")
                .setPositiveButton("부활") { _, _ ->
                    Wallet.useItem(this, "item_revive")
                    refreshItemBar()
                    adapter.add(ChatItem.Sys("🛡 부활했어요!"))
                    scrollToEnd()
                    beginPlayerTurn()
                }
                .setNegativeButton("포기") { _, _ -> finishGame(win, reason) }
                .setCancelable(false)
                .show()
            return
        }
        finishGame(win, reason)
    }

    private fun finishGame(win: Boolean, reason: String) {
        gameOver = true
        stopTimer()
        setInputEnabled(false)
        audio.stopBgm()

        if (win) {
            audio.play("sfx_win")
            vibrate(100, 50, 100, 50, 100)
        }

        val prefs = getSharedPreferences("kkeutmal", MODE_PRIVATE)
        val bestScore = prefs.getInt("best_score", 0)
        val bestRound = prefs.getInt("best_round", 0)
        val newBest = engine.score > bestScore || engine.round > bestRound
        prefs.edit()
            .putInt("best_score", maxOf(bestScore, engine.score))
            .putInt("best_round", maxOf(bestRound, engine.round))
            .apply()

        val outcome = GameOutcome(
            mode = mode,
            won = win,
            score = engine.score,
            rounds = engine.round,
            stage = stageNumber,
            isBoss = stageConfig?.boss != null,
            doubleReward = doubleRewardActive
        )
        val reward = GameResult.rewardFor(outcome)
        if (reward.coins > 0) Wallet.addCoins(this, reward.coins)
        val levelsGained = if (reward.xp > 0) Wallet.addXp(this, reward.xp) else 0
        Wallet.recordRounds(this, engine.round)
        if (mode == GameMode.ADVENTURE && win) {
            Wallet.setStage(this, stageNumber + 1)
        }

        fun bump(m: Mission, amount: Int) {
            if (amount <= 0) return
            val key = "mission_progress_${m.id}"
            prefs.edit().putInt(key, Missions.applyProgress(m, prefs.getInt(key, 0), amount)).apply()
        }
        bump(Mission.PLAY_3, 1)
        bump(Mission.ROUNDS_5, engine.round)
        bump(Mission.LONG_WORD_3, longWordCount)
        bump(Mission.VOICE_5, voiceWordCount)
        bump(Mission.SCORE_300, engine.score)
        if (win && mode == GameMode.ADVENTURE) bump(Mission.STAGE_2, 1)
        if (win && reason.contains("항복")) bump(Mission.SURRENDER_1, 1)

        val newLevel = Wallet.level(this)
        AvatarCatalog.ALL.forEach { def ->
            val u = def.unlock
            val unlocked = when (u) {
                is Unlock.Level -> u.level <= newLevel
                is Unlock.BossClear -> win && stageConfig?.boss != null && u.stage == stageNumber
                else -> false
            }
            if (unlocked) Wallet.ownAvatarId(this, def.id)
        }
        // 도전과제는 이번 판 기록이 반영된 뒤에 판정한다
        Achievements.metBy(Wallet.playerStats(this)).forEach { ach ->
            AvatarCatalog.ALL
                .filter { it.unlock is Unlock.Achieve && (it.unlock as Unlock.Achieve).achievementId == ach.id }
                .forEach { Wallet.ownAvatarId(this, it.id) }
        }

        handler.postDelayed({ showResultDialog(win, reason, newBest, reward, levelsGained) }, 700L)
    }

    private fun vibrate(vararg ms: Long) {
        if (vibrator?.hasVibrator() != true) return
        if (ms.size == 1) {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms[0], VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator?.vibrate(VibrationEffect.createWaveform(ms, -1))
        }
    }

    private fun showResultDialog(win: Boolean, reason: String, newBest: Boolean, reward: Reward, levelsGained: Int) {
        if (isDestroyed || isFinishing) return
        val b = DialogResultBinding.inflate(LayoutInflater.from(this))
        b.tvEmoji.text = if (win) "🏆" else "💀"
        b.tvTitle.text = if (win) "승리!" else "패배…"
        b.tvReason.text = reason
        b.tvStatRound.text = "${engine.round}라운드"
        b.tvStatScore.text = "${engine.score}점"
        b.tvNewBest.visibility = if (newBest && engine.score > 0) View.VISIBLE else View.GONE
        if (reward.xp > 0) {
            b.tvXpEarned.visibility = View.VISIBLE
            b.tvXpEarned.text = "✨ +${reward.xp} XP"
        }
        if (levelsGained > 0) {
            b.tvLevelUp.visibility = View.VISIBLE
            b.tvLevelUp.text = "🎊 레벨 ${Wallet.level(this)} 달성!"
        }
        if (reward.coins > 0) {
            b.tvCoinsEarned.visibility = View.VISIBLE
            b.tvCoinsEarned.text = "🪙 +${reward.coins} 코인"
        } else {
            b.tvCoinsEarned.visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(b.root)
            .setCancelable(false)
            .create()
        b.btnRetry.setOnClickListener {
            dialog.dismiss()
            recreate()
        }
        b.btnHome.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                voice.start()
            } else {
                showError("마이크 권한을 허용해야 음성 입력을 쓸 수 있어요")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (voice.listening) voice.cancel()
        audio.stopBgm()
    }

    override fun onResume() {
        super.onResume()
        if (!gameOver) audio.startBgm()
        refreshItemBar()
    }

    override fun onDestroy() {
        stopTimer()
        handler.removeCallbacksAndMessages(null)
        audio.cleanup()
        voice.cleanup()
        super.onDestroy()
    }
}
