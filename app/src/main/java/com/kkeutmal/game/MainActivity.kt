package com.kkeutmal.game

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kkeutmal.game.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var level = AiLevel.NORMAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

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

        // 프로필
        val playerLevel = Wallet.level(this)
        val rank = Progress.rankOf(playerLevel)
        binding.avatarMe.bind(AvatarCatalog.byId(Wallet.selectedAvatarId(this)))
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
