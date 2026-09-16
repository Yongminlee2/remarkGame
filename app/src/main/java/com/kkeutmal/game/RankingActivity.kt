package com.kkeutmal.game

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kkeutmal.game.databinding.ActivityRankingBinding

/**
 * 랭킹 — 모험 최고 스테이지 순위표.
 *
 * **올릴지는 사용자가 정한다.** 처음 들어오면 물어보고, 올리기로 한 사람만 기록이 서버에 간다.
 * 순위표를 보기만 하는 사람의 기록을 말없이 공개 목록에 올리지 않는다. 언제든 내릴 수 있다.
 */
class RankingActivity : AppCompatActivity() {

    companion object {
        private const val TOP = 50
        private const val KEY_OPT = "ranking_opt" // "in" / "out" / 없음(아직 안 물어봄)

        fun optedIn(ctx: Context): Boolean =
            ctx.getSharedPreferences("kkeutmal", Context.MODE_PRIVATE).getString(KEY_OPT, null) == "in"

        /**
         * 모험 스테이지를 깬 뒤 부른다. 올리기로 한 사람만, 잠깐 연결해 올리고 바로 끊는다.
         */
        fun syncInBackground(ctx: Context) {
            if (!optedIn(ctx)) return
            val app = ctx.applicationContext
            Online.acquire(
                onReady = { uid ->
                    Ranking.syncMine(uid, Wallet.bestStage(app), Wallet.selectedAvatarId(app)) {
                        Online.release()
                    }
                },
                onError = { Online.release() }
            )
        }
    }

    private lateinit var binding: ActivityRankingBinding
    private var uid: String? = null

    private val prefs get() = getSharedPreferences("kkeutmal", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRankingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        binding.btnBack.setOnClickListener { finish() }

        binding.avatarMe.bind(AvatarCatalog.byIdOrDefault(Wallet.selectedAvatarId(this)))
        binding.tvMine.text = "${Wallet.bestStage(this)}스테이지"
        binding.btnRemove.setOnClickListener { toggleOpt() }

        Online.acquire(
            onReady = { id ->
                if (isFinishing || isDestroyed) return@acquire
                uid = id
                when (prefs.getString(KEY_OPT, null)) {
                    null -> askOpt()
                    "in" -> syncThenLoad()
                    else -> load()
                }
            },
            onError = { msg -> binding.tvStatus.text = msg }
        )
    }

    private fun askOpt() {
        MaterialAlertDialogBuilder(this)
            .setTitle("순위표에 내 기록을 올릴까요?")
            .setMessage(
                "아바타와 모험 최고 스테이지만 올라가요. 이름이나 연락처는 올라가지 않아요.\n" +
                    "올리지 않아도 순위표는 볼 수 있고, 나중에 언제든 바꿀 수 있어요."
            )
            .setPositiveButton("올리기") { _, _ ->
                prefs.edit().putString(KEY_OPT, "in").apply()
                syncThenLoad()
            }
            .setNegativeButton("보기만") { _, _ ->
                prefs.edit().putString(KEY_OPT, "out").apply()
                load()
            }
            .setCancelable(false)
            .show()
    }

    private fun syncThenLoad() {
        val me = uid ?: return
        binding.tvStatus.text = "내 기록을 올리는 중…"
        Ranking.syncMine(me, Wallet.bestStage(this), Wallet.selectedAvatarId(this)) { onServer ->
            if (isFinishing || isDestroyed) return@syncMine
            val local = Wallet.bestStage(this)
            // 서버 규칙이 한 번에 올려 주는 만큼만 올라간다 — 따라 올라가는 중임을 알려 준다
            binding.tvMineNote.visibility =
                if (onServer != null && onServer < local && local >= 2) View.VISIBLE else View.GONE
            binding.tvMineNote.text = "순위표에는 ${onServer}스테이지로 올라가 있어요. 시간이 지나며 따라 올라가요"
            load()
        }
    }

    private fun load() {
        binding.tvStatus.text = "불러오는 중…"
        binding.btnRemove.visibility = View.VISIBLE
        binding.btnRemove.text =
            if (optedIn(this)) "순위표에서 내 기록 내리기" else "순위표에 내 기록 올리기"
        Ranking.top(
            TOP,
            onResult = { list ->
                if (isFinishing || isDestroyed) return@top
                render(list)
            },
            onError = { msg -> if (!isFinishing) binding.tvStatus.text = msg }
        )
    }

    private fun render(list: List<Ranking.Entry>) {
        binding.list.removeAllViews()
        binding.tvStatus.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.tvStatus.text = "아직 순위표가 비어 있어요. 첫 번째가 되어 보세요!"
        val me = uid
        var rank = 0
        var prevStage = -1
        list.forEachIndexed { i, e ->
            // 같은 스테이지는 같은 순위로 매긴다
            if (e.stage != prevStage) rank = i + 1
            prevStage = e.stage
            binding.list.addView(row(rank, e, isMe = e.uid == me))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun row(rank: Int, e: Ranking.Entry, isMe: Boolean): View {
        val def = AvatarCatalog.byIdOrDefault(e.avatar)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12))
            background = ContextCompat.getDrawable(
                this@RankingActivity, if (isMe) R.drawable.bg_cell_selected else R.drawable.bg_input
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(3)) }
        }
        row.addView(TextView(this).apply {
            text = when (rank) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "$rank"
            }
            textSize = if (rank <= 3) 22f else 16f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@RankingActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(AvatarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(dp(6), 0, dp(10), 0) }
            bind(def)
        })
        row.addView(TextView(this).apply {
            text = if (isMe) "${def.name} (나)" else def.name
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@RankingActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = "${e.stage}스테이지"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@RankingActivity, R.color.accent2))
        })
        return row
    }

    private fun toggleOpt() {
        val me = uid ?: return
        if (optedIn(this)) {
            prefs.edit().putString(KEY_OPT, "out").apply()
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = "내리는 중…"
            Ranking.removeMine(me) { if (!isFinishing && !isDestroyed) load() }
        } else {
            prefs.edit().putString(KEY_OPT, "in").apply()
            syncThenLoad()
        }
    }

    override fun onDestroy() {
        Online.release()
        super.onDestroy()
    }
}
