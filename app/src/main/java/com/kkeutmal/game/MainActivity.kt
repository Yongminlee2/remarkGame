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
        if (Wallet.settleAbandonedGame(this)) {
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
        binding.swNoTimer.isChecked = prefs.getBoolean("sel_notimer", false)

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
        binding.btnCollection.setOnClickListener {
            startActivity(Intent(this, CollectionActivity::class.java))
        }
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
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

        // 한 판도 안 한 사람에게 "0승 0패"를 보여 줄 이유가 없다. 줄째로 감춘다.
        val record = Wallet.recordText(Wallet.wins(this), Wallet.losses(this))
        binding.rowRecord.visibility = if (record == null) View.GONE else View.VISIBLE
        val streak = Wallet.winStreak(this)
        binding.tvRecord.text =
            if (record != null && streak >= 2) "$record  🔥${streak}연승" else record.orEmpty()

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
            for (m in Mission.entries) editor.putInt("mission_progress_${m.id}", 0)
            editor.putBoolean("missions_bonus_paid", false).apply()
        }

        val ids = prefs.getString("missions_ids", "")!!.split(",").filter { it.isNotEmpty() }
        val missions = ids.mapNotNull { id -> Mission.entries.firstOrNull { it.id == id } }

        binding.missionBox.removeAllViews()
        binding.missionBox.addView(TextView(this).apply {
            text = "📋 오늘의 미션"
            textSize = 13f
            setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.text_dim))
        })
        for (m in missions) {
            val progress = prefs.getInt("mission_progress_${m.id}", 0)
            val done = Missions.isComplete(m, progress)
            binding.missionBox.addView(TextView(this).apply {
                text = if (done) "✅ ${m.label}" else "・${m.label}  ($progress/${m.target})"
                textSize = 13f
                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, 0)
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        this@MainActivity,
                        if (done) R.color.accent2 else R.color.text_primary
                    )
                )
            })
        }
    }
}
