package com.kkeutmal.game

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kkeutmal.game.databinding.ActivityAboutBinding

/**
 * 정보·출처 화면.
 *
 * 뜻풀이 데이터가 CC BY-SA 2.0 KR 이라 **저작자 표시가 법적 의무**다.
 * 이 화면을 지우거나 항목을 빼면 라이선스 위반이 된다.
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    private data class Credit(
        val what: String,
        val source: String,
        val license: String
    )

    private val credits = listOf(
        Credit(
            "단어 뜻풀이",
            "국립국어원 표준국어대사전",
            "CC BY-SA 2.0 KR (저작자표시-동일조건변경허락)"
        ),
        Credit(
            "표제어 목록",
            "국립국어원 표준국어대사전 기반 공개 자료",
            "단어 목록 자체는 저작권 보호 대상이 아님"
        ),
        Credit(
            "아바타·UI 그래픽",
            "Kenney (kenney.nl) — Shape Characters, Game Icons",
            "CC0 1.0 (퍼블릭 도메인)"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        binding.btnBack.setOnClickListener { finish() }

        binding.tvVersion.text = "버전 ${BuildConfig.VERSION_NAME}"
        buildCredits()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildCredits() {
        binding.creditBox.removeAllViews()
        for (c in credits) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = ContextCompat.getDrawable(this@AboutActivity, R.drawable.bg_input)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(4), 0, dp(4)) }
            }
            card.addView(TextView(this).apply {
                text = c.what
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@AboutActivity, R.color.text_primary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            card.addView(TextView(this).apply {
                text = c.source
                textSize = 13f
                setPadding(0, dp(3), 0, 0)
                setTextColor(ContextCompat.getColor(this@AboutActivity, R.color.accent2))
            })
            card.addView(TextView(this).apply {
                text = c.license
                textSize = 12f
                setPadding(0, dp(2), 0, 0)
                setTextColor(ContextCompat.getColor(this@AboutActivity, R.color.text_dim))
            })
            binding.creditBox.addView(card)
        }
    }
}
