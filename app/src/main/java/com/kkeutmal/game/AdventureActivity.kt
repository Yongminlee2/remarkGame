package com.kkeutmal.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kkeutmal.game.databinding.ActivityAdventureBinding

class AdventureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdventureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdventureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnChallenge.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_MODE, GameMode.ADVENTURE.name)
                    .putExtra(GameActivity.EXTRA_STAGE, Wallet.stage(this))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val n = Wallet.stage(this)
        val cfg = Stage.configFor(n)

        binding.tvStageNo.text = "${n}스테이지"
        binding.tvBestStage.text = "🏔 최고 ${Wallet.bestStage(this)}"
        binding.tvStageInfo.text = buildString {
            append("목표 ${cfg.targetRounds}라운드 버티기\n")
            append("제한시간 ${cfg.timerSec}초 · AI ${cfg.aiLevel.label}")
            if (cfg.allowHanbang) append("\n⚠ AI가 한방단어를 씁니다")
        }

        val boss = cfg.boss
        val ruleText = cfg.ruleLabel

        if (boss != null) {
            binding.tvBossInfo.visibility = View.VISIBLE
            binding.tvBossInfo.setTextColor(ContextCompat.getColor(this, R.color.error))
            binding.tvBossInfo.text =
                if (ruleText.isEmpty()) "👹 보스 ${boss.name}" else "👹 보스 ${boss.name}\n$ruleText"
            binding.avatarBoss.visibility = View.VISIBLE
            AvatarCatalog.byId("rhombus_red_special")?.let { binding.avatarBoss.bindBoss(it) }
            binding.tvNextBoss.text = "지금이 보스 스테이지입니다"
        } else {
            // 일반 스테이지도 규칙이 하나씩 걸리므로 도전 전에 미리 보여준다
            binding.avatarBoss.visibility = View.GONE
            if (ruleText.isEmpty()) {
                binding.tvBossInfo.visibility = View.GONE
            } else {
                binding.tvBossInfo.visibility = View.VISIBLE
                binding.tvBossInfo.setTextColor(ContextCompat.getColor(this, R.color.warn))
                binding.tvBossInfo.text = "📜 $ruleText"
            }
            binding.tvNextBoss.text = "다음 보스까지 ${Stage.stagesToNextBoss(n)}스테이지"
        }
    }
}
