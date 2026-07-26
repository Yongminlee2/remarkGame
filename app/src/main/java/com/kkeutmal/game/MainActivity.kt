package com.kkeutmal.game

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kkeutmal.game.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var level = AiLevel.NORMAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        val bestScore = prefs.getInt("best_score", 0)
        val bestRound = prefs.getInt("best_round", 0)
        binding.tvBest.text =
            if (bestScore == 0 && bestRound == 0) "아직 기록 없음"
            else "${bestScore}점 · ${bestRound}라운드"
        binding.tvCoins.text = "🪙 ${Wallet.coins(this)} 코인"
    }
}
