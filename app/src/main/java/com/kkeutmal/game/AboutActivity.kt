package com.kkeutmal.game

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

    companion object {
        const val MAIL = "dydals5678@gmail.com"

        /**
         * 바깥으로 나가는 링크는 모두 **legal 저장소**를 가리킨다.
         *
         * 예전에는 소스 저장소(remarkGame)를 가리켰다. 그런데 그 저장소는 언제든
         * private 으로 돌릴 수 있고, 그러면 링크가 전부 404 가 된다.
         * 특히 **개인정보처리방침 링크가 죽으면 스토어 정책 위반**이라 앱이 내려갈 수 있다.
         * 그래서 문서만 따로 떼어 계속 공개로 둘 legal 저장소에 두고, 앱은 그쪽만 본다.
         */
        const val DOCS = "https://yongminlee2.github.io/legal/wordchain/"
        const val PRIVACY = "https://yongminlee2.github.io/legal/wordchain/privacy.html"
        const val SUPPORT = "https://yongminlee2.github.io/legal/wordchain/support.html"
    }

    private lateinit var binding: ActivityAboutBinding

    private data class Credit(
        val what: String,
        val source: String,
        val license: String
    )

    private val credits = listOf(
        Credit(
            "단어 뜻풀이",
            "국립국어원 표준국어대사전 (stdict.korean.go.kr 공개 배포본)",
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

    /**
     * 원본에 뜻풀이가 없는 낱말은 어근에서 규칙으로 만들어 채웠다.
     * 사전이 실제로 그렇게 적었다고 오해하지 않도록 이 사실을 밝혀 둔다.
     */
    private val meaningNote =
        "동음이의어는 사전에 실린 여러 뜻 가운데 가장 널리 쓰이는 것을 골라 보여 줍니다. " +
            "원본 사전에 뜻풀이가 아예 없는 파생어·합성어(전체의 약 4%)는 어근의 뜻에서 " +
            "규칙으로 만들어 채웠습니다. \"'가가대소'를 하다\"처럼 어근을 가리키는 문장이 " +
            "그렇게 만들어진 것입니다."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        binding.btnBack.setOnClickListener { finish() }

        // 문의가 들어왔을 때 어느 빌드인지 바로 알 수 있게 버전 이름과 번호를 함께 적는다
        binding.tvVersion.text =
            "버전 ${BuildConfig.VERSION_NAME} (빌드 ${BuildConfig.VERSION_CODE})"

        binding.tvMail.setOnClickListener {
            open(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$MAIL")).putExtra(
                    Intent.EXTRA_SUBJECT,
                    "끝말잇기 문의 (v${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE})"
                ),
                "메일 앱을 찾을 수 없어요"
            )
        }
        binding.tvDocs.setOnClickListener {
            open(Intent(Intent.ACTION_VIEW, Uri.parse(DOCS)), "브라우저를 찾을 수 없어요")
        }
        binding.tvSupport.setOnClickListener {
            open(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT)), "브라우저를 찾을 수 없어요")
        }
        binding.tvPrivacyPolicy.setOnClickListener {
            open(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY)), "브라우저를 찾을 수 없어요")
        }

        binding.tvMeaningNote.text = meaningNote
        buildCredits()
    }

    /** 상대 앱이 없을 수도 있으니 실패해도 앱이 죽지 않게 감싼다 */
    private fun open(intent: Intent, failMessage: String) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, failMessage, Toast.LENGTH_SHORT).show()
        }
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
