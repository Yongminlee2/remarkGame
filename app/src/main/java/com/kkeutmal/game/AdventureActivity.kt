package com.kkeutmal.game

import android.content.Intent
import android.graphics.drawable.GradientDrawable
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
            showRule(
                if (ruleText.isEmpty()) "👹 보스 ${boss.name}" else "👹 보스 ${boss.name}\n$ruleText",
                R.color.error
            )
            binding.avatarBoss.visibility = View.VISIBLE
            // 보스 초상: 붉은 계열의 개성 있는 아바타를 쓰고 bindBoss 가 화난 입으로 바꾼다.
            // 아이디를 박아두면 카탈로그가 바뀔 때 조용히 사라지므로 조건으로 찾는다.
            val bossFace = AvatarCatalog.ALL.firstOrNull {
                it.color == AvatarColor.RED && it.grade != AvatarGrade.COMMON
            } ?: AvatarCatalog.byIdOrDefault(AvatarCatalog.DEFAULT_ID)
            binding.avatarBoss.bindBoss(bossFace)
            binding.tvNextBoss.text = "지금이 보스 스테이지입니다"
        } else {
            // 일반 스테이지도 규칙이 하나씩 걸리므로 도전 전에 미리 보여준다
            binding.avatarBoss.visibility = View.GONE
            if (ruleText.isEmpty()) {
                binding.tvBossInfo.visibility = View.GONE
            } else {
                showRule("📜 $ruleText", R.color.warn)
            }
            binding.tvNextBoss.text = "다음 보스까지 ${Stage.stagesToNextBoss(n)}스테이지"
        }
    }

    /** 조건 배너. 글자와 테두리를 같은 색으로 맞춰야 눈에 확 들어온다. */
    private fun showRule(text: String, colorRes: Int) {
        val accent = ContextCompat.getColor(this, colorRes)
        binding.tvBossInfo.visibility = View.VISIBLE
        binding.tvBossInfo.text = text
        binding.tvBossInfo.setTextColor(accent)
        (ContextCompat.getDrawable(this, R.drawable.bg_rule_banner) as? GradientDrawable)?.let { bg ->
            bg.mutate()
            bg.setStroke((2 * resources.displayMetrics.density).toInt(), accent)
            binding.tvBossInfo.background = bg
        }
    }
}
