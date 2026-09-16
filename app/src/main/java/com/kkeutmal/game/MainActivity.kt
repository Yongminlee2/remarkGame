package com.kkeutmal.game

import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kkeutmal.game.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var level = AiLevel.NORMAL

    /**
     * 인앱 업데이트 창의 결과를 받는 자리.
     *
     * 결과를 따로 볼 것은 없다 — 유연 업데이트라 사용자가 거절해도 게임은 그대로
     * 돌아가고, 수락하면 내려받기는 뒤에서 알아서 진행된다. 다만 결과를 받을
     * 창구가 있어야 시스템이 창을 띄워 주므로 등록만 해 둔다.
     */
    private val updateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    /**
     * 제목에 그라데이션을 입힌다.
     *
     * TextView 는 색을 하나만 받으므로 페인트에 셰이더를 직접 건다.
     * 셰이더는 픽셀 좌표로 도니까 글자가 실제로 배치된 **뒤에** 폭을 알 수 있다.
     * 그래서 doOnLayout 처럼 한 번 그려진 다음에 건다.
     */
    private fun paintTitle() {
        val tv = binding.tvTitle
        tv.post {
            val w = tv.width.toFloat()
            if (w <= 0f) return@post
            tv.paint.shader = LinearGradient(
                0f, 0f, w, tv.height.toFloat(),
                intArrayOf(
                    ContextCompat.getColor(this, R.color.title_grad_start),
                    ContextCompat.getColor(this, R.color.title_grad_mid),
                    ContextCompat.getColor(this, R.color.title_grad_end)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            tv.invalidate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        paintTitle()

        // 지난번에 판을 끝내지 않고 앱을 꺼 버렸으면 그 판을 패배로 센다.
        //
        // **onResume 이 아니라 onCreate 에 둔다.** 게임에서 돌아올 때는 onResume 만 도는데,
        // 거기 두면 정상적으로 끝낸 판까지 한 번 더 세게 된다. onCreate 는 앱이 새로
        // 켜질 때만 돌므로 "꺼 버린 판" 만 걸린다.
        // 온라인 대전 중에 꺼진 판도 같은 약속으로 센다. 전적이 따로라 따로 정산한다.
        val abandonedOnline = Wallet.settleAbandonedOnline(this)
        if (Wallet.settleAbandonedGame(this) || abandonedOnline) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("지난 판이 패배로 기록됐어요")
                .setMessage(
                    "게임 중에 앱이 종료돼서 그 판은 진 것으로 처리했어요.\n" +
                        "항복 버튼으로 끝내면 언제든 깔끔하게 마칠 수 있어요."
                )
                .setPositiveButton("확인", null)
                .show()
        }

        // 동의 확인 후 광고 SDK 를 시작한다. 여기서 한 번만 부르면 된다.
        Ads.start(this)
        Ads.attachBanner(this, binding.adContainer)
        Wallet.ensureStarterGrant(this)

        WordDict.preload(this) // 미리 로드해서 게임 진입을 빠르게

        val prefs = getSharedPreferences("kkeutmal", MODE_PRIVATE)
        level = runCatching { AiLevel.valueOf(prefs.getString("sel_level", AiLevel.NORMAL.name)!!) }
            .getOrDefault(AiLevel.NORMAL)
        // 스위치를 감춰 뒀으므로 저장값을 읽지 않는다. 예전에 켜 둔 사람이 있으면
        // 끌 방법이 없어 영영 무제한으로 남기 때문에, 저장값도 함께 꺼 준다.
        binding.swNoTimer.isChecked = false
        if (prefs.getBoolean("sel_notimer", false)) {
            prefs.edit().putBoolean("sel_notimer", false).apply()
        }

        binding.toggleDifficulty.check(buttonIdOf(level))
        updateDesc()

        binding.toggleDifficulty.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            level = when (checkedId) {
                binding.btnVeryEasy.id -> AiLevel.VERY_EASY
                binding.btnEasy.id -> AiLevel.EASY
                binding.btnHard.id -> AiLevel.HARD
                else -> AiLevel.NORMAL
            }
            prefs.edit().putString("sel_level", level.name).apply()
            updateDesc()
        }
        binding.swNoTimer.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("sel_notimer", checked).apply()
            updateDesc()
        }

        binding.btnShop.setOnClickListener {
            startActivity(Intent(this, ShopActivity::class.java))
        }
        binding.btnAdventure.setOnClickListener {
            startActivity(Intent(this, AdventureActivity::class.java))
        }
        binding.btnOnline.setOnClickListener {
            startActivity(Intent(this, OnlineLobbyActivity::class.java))
        }
        binding.btnRanking.setOnClickListener {
            startActivity(Intent(this, RankingActivity::class.java))
        }
        binding.btnCollection.setOnClickListener {
            startActivity(Intent(this, CollectionActivity::class.java))
        }
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // 규칙은 접어 두고 제목을 누르면 펼친다. 처음 한 번만 읽으면 되는 내용이
        // 늘 펼쳐져 있으면 화면 아래가 텍스트 벽이 된다.
        fun renderRules() {
            val open = binding.tvRulesBody.visibility == View.VISIBLE
            binding.tvRulesTitle.text =
                getString(R.string.rules_title) + if (open) "   ▲" else "   ▼"
        }
        renderRules()
        binding.tvRulesTitle.setOnClickListener {
            binding.tvRulesBody.visibility =
                if (binding.tvRulesBody.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            renderRules()
        }

        binding.btnStart.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_LEVEL, level.name)
                    .putExtra(GameActivity.EXTRA_NO_TIMER, binding.swNoTimer.isChecked)
            )
        }
    }

    private fun buttonIdOf(l: AiLevel) = when (l) {
        AiLevel.VERY_EASY -> binding.btnVeryEasy.id
        AiLevel.EASY -> binding.btnEasy.id
        AiLevel.NORMAL -> binding.btnNormal.id
        AiLevel.HARD -> binding.btnHard.id
    }

    private fun updateDesc() {
        val timerPart =
            if (binding.swNoTimer.isChecked) "제한시간 없음" else "제한시간 ${level.timerSec}초"
        binding.tvDifficultyDesc.text = "$timerPart · ${level.desc}"
    }

    override fun onResume() {
        super.onResume()

        // 새 버전 확인. 받아 두고 설치를 미룬 경우도 여기서 다시 권하므로,
        // 처음 켤 때가 아니라 홈에 들어올 때마다 부른다.
        AppUpdate.check(this, updateLauncher)

        val prefs = getSharedPreferences("kkeutmal", MODE_PRIVATE)
        var shownDialogThisResume = false

        // 출석 보상 — 홈에 들어올 때 하루 한 번만 처리
        val todayKey = java.time.LocalDate.now().toString()
        val lastKey = prefs.getString("streak_last_date", null)
        if (lastKey != todayKey) {
            val r = Missions.advanceStreak(lastKey, todayKey, prefs.getInt("streak_days", 0))
            prefs.edit()
                .putString("streak_last_date", todayKey)
                .putInt("streak_days", r.days)
                .putInt("streak_best", maxOf(prefs.getInt("streak_best", 0), r.days))
                .apply()
            if (r.reward > 0) {
                shownDialogThisResume = true
                Wallet.addCoins(this, r.reward)
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("🔥 ${r.days}일 연속 출석!")
                    .setMessage("🪙 +${r.reward} 코인을 받았어요")
                    .setPositiveButton("확인", null)
                    .show()
            }
        }

        val bestScore = prefs.getInt("best_score", 0)
        val bestRound = prefs.getInt("best_round", 0)
        binding.tvBest.text =
            if (bestScore == 0 && bestRound == 0) "아직 기록 없음"
            else "${bestScore}점 · ${bestRound}라운드"
        binding.tvCoins.text = "🪙 ${Wallet.coins(this)} 코인"

        val wins = Wallet.wins(this)
        val losses = Wallet.losses(this)
        binding.tvWins.text = "$wins"
        binding.tvLosses.text = "$losses"
        // 한 판도 안 했으면 0% 가 아니라 "-" — 진 적이 없는데 0% 로 보이면 억울하다
        binding.tvWinRate.text = Wallet.winRatePercent(wins, losses)?.let { "$it%" } ?: "-"
        val streak = Wallet.winStreak(this)
        binding.tvStreak.visibility = if (streak >= 2) View.VISIBLE else View.GONE
        binding.tvStreak.text = "🔥 ${streak}연승 중"
        binding.tvOnlineWins.text = "${Wallet.onlineWins(this)}승"
        binding.tvOnlineLosses.text = "${Wallet.onlineLosses(this)}패"

        // 프로필
        val playerLevel = Wallet.level(this)
        val rank = Progress.rankOf(playerLevel)
        binding.avatarMe.bind(AvatarCatalog.byIdOrDefault(Wallet.selectedAvatarId(this)))
        binding.tvLevelRank.text = "Lv.$playerLevel · ${rank.label}"
        if (playerLevel >= Progress.MAX_LEVEL) {
            binding.xpBar.progress = 1000
            binding.tvXp.text = "MAX"
        } else {
            val into = Progress.xpIntoLevel(Wallet.xp(this))
            val need = Progress.xpForNextLevel(playerLevel)
            binding.xpBar.progress = if (need > 0) (into * 1000 / need).coerceIn(0, 1000) else 1000
            binding.tvXp.text = "$into / $need XP"
        }

        renderMissions()

        // 리뷰 창은 출석 보상 창과 겹치지 않을 때만. 창 두 개가 연달아 뜨면
        // 어느 쪽도 제대로 안 읽힌다. 못 띄운 날은 다음에 홈에 들어올 때 다시 본다.
        if (!shownDialogThisResume) Review.maybeAsk(this)
    }

    private fun renderMissions() {
        val today = java.time.LocalDate.now().toString()
        val prefs = getSharedPreferences("kkeutmal", MODE_PRIVATE)

        // 날짜가 바뀌었으면 오늘의 미션을 새로 뽑고 진행도를 초기화한다
        if (prefs.getString("missions_date", null) != today) {
            val picked = Missions.pickDaily(today)
            val editor = prefs.edit()
                .putString("missions_date", today)
                .putString("missions_ids", picked.joinToString(",") { it.id })
            for (m in Mission.entries) {
                editor.putInt("mission_progress_${m.id}", 0)
                editor.putBoolean("mission_paid_${m.id}", false)
            }
            editor.putBoolean("missions_bonus_paid", false).apply()
        }

        val ids = prefs.getString("missions_ids", "")!!.split(",").filter { it.isNotEmpty() }
        val missions = ids.mapNotNull { id -> Mission.entries.firstOrNull { it.id == id } }

        // 깬 미션 보상을 여기서 준다. 판이 끝나는 곳이 여럿(AI·온라인·모험)이라
        // 홈으로 돌아올 때 한 곳에서 몰아 주는 편이 빠뜨리지 않는다.
        val payout = Missions.payout(
            missions,
            progress = { prefs.getInt("mission_progress_${it.id}", 0) },
            alreadyPaid = { prefs.getBoolean("mission_paid_${it.id}", false) },
            bonusPaid = prefs.getBoolean("missions_bonus_paid", false)
        )
        if (payout.coins > 0) {
            val editor = prefs.edit()
            payout.paid.forEach { editor.putBoolean("mission_paid_${it.id}", true) }
            if (payout.bonus) editor.putBoolean("missions_bonus_paid", true)
            editor.apply()
            Wallet.addCoins(this, payout.coins)
            binding.tvCoins.text = "🪙 ${Wallet.coins(this)} 코인"
            android.widget.Toast.makeText(
                this,
                (if (payout.bonus) "🎉 오늘의 미션 모두 완료! " else "📋 미션 완료! ") + "🪙 +${payout.coins} 코인",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        val color = { res: Int -> androidx.core.content.ContextCompat.getColor(this, res) }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        binding.missionBox.removeAllViews()
        binding.missionBox.addView(android.widget.LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "📋 오늘의 미션"
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(color(R.color.text_primary))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = if (prefs.getBoolean("missions_bonus_paid", false)) "🎉 보너스 받음"
                else "모두 깨면 🪙+${Missions.ALL_CLEAR_BONUS}"
                textSize = 12f
                setTextColor(color(R.color.text_dim))
            })
        })

        for (m in missions) {
            val progress = prefs.getInt("mission_progress_${m.id}", 0)
            val done = Missions.isComplete(m, progress)
            binding.missionBox.addView(android.widget.LinearLayout(this).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
                addView(TextView(this@MainActivity).apply {
                    text = if (done) "✅ ${m.label}" else "・${m.label}  ($progress/${m.target})"
                    textSize = 13f
                    setTextColor(color(if (done) R.color.accent2 else R.color.text_primary))
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
                })
                // 무엇을 받는지 보여야 할 맛이 난다
                addView(TextView(this@MainActivity).apply {
                    text = if (done) "받음" else "🪙${m.reward}"
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(color(if (done) R.color.text_dim else R.color.warn))
                })
            })
            // 숫자만으로는 얼마나 왔는지 읽어야 안다. 막대를 깔면 한눈에 들어온다.
            // 끝낸 미션은 이미 ✅ 로 알 수 있으니 막대를 그리지 않는다.
            if (!done) {
                binding.missionBox.addView(
                    com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
                        max = 1000
                        setProgressCompat(
                            (progress.toLong() * 1000 / m.target.coerceAtLeast(1))
                                .toInt().coerceIn(0, 1000),
                            false
                        )
                        trackThickness = dp(4)
                        trackCornerRadius = dp(2)
                        // Material 1.13 부터 막대 끝에 점이 기본으로 붙는다.
                        // 진행이 0 인 얇은 막대에서는 얼룩처럼 보여 끈다.
                        trackStopIndicatorSize = 0
                        setTrackColor(
                            androidx.core.content.ContextCompat.getColor(
                                this@MainActivity, R.color.chip_bg
                            )
                        )
                        setIndicatorColor(
                            androidx.core.content.ContextCompat.getColor(
                                this@MainActivity, R.color.accent2
                            )
                        )
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(dp(2), dp(5), dp(2), 0) }
                    }
                )
            }
        }
    }
}
