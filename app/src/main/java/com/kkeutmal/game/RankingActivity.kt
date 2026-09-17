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
 * 랭킹 — 순위표 두 가지.
 * - 스테이지: 스테이지 도전 최고 기록
 * - 온라인 대전: 랜덤 매칭 승패. **폰의 전적이 아니라 서버가 판마다 센 기록**이다
 *   (광고로 패배를 지워도 여기는 그대로 — [OnlineRules] 의 온라인 대전 순위 절).
 *
 * **올릴지는 사용자가 정한다.** 처음 들어오면 물어보고, 올리기로 한 사람만 순위표에 보인다.
 * 순위표를 보기만 하는 사람의 기록을 말없이 공개 목록에 올리지 않는다. 언제든 내릴 수 있다.
 */
class RankingActivity : AppCompatActivity() {

    companion object {
        private const val TOP = 50
        /** 온라인은 올리기를 안 고른 사람도 서버에 세어져 있어 걸러낼 몫까지 넉넉히 읽는다 */
        private const val ONLINE_READ = 100
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

    private enum class Tab { STAGE, ONLINE }

    private lateinit var binding: ActivityRankingBinding
    private var uid: String? = null
    private var tab = Tab.STAGE

    private val prefs get() = getSharedPreferences("kkeutmal", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRankingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        binding.btnBack.setOnClickListener { finish() }

        binding.avatarMe.bind(AvatarCatalog.byIdOrDefault(Wallet.selectedAvatarId(this)))
        binding.btnRemove.setOnClickListener { toggleOpt() }
        binding.tabs.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            tab = if (id == binding.tabOnline.id) Tab.ONLINE else Tab.STAGE
            showTab()
        }
        renderHeader()

        Online.acquire(
            onReady = { id ->
                if (isFinishing || isDestroyed) return@acquire
                uid = id
                when (prefs.getString(KEY_OPT, null)) {
                    null -> askOpt()
                    "in" -> syncThenLoad()
                    else -> showTab()
                }
            },
            onError = { msg -> binding.tvStatus.text = msg }
        )
    }

    private fun askOpt() {
        MaterialAlertDialogBuilder(this)
            .setTitle("순위표에 내 기록을 올릴까요?")
            .setMessage(
                "아바타와 스테이지 도전 최고 기록, 랜덤 매칭 승패가 순위표에 보여요. " +
                    "이름이나 연락처는 올라가지 않아요.\n" +
                    "올리지 않아도 순위표는 볼 수 있고, 나중에 언제든 바꿀 수 있어요."
            )
            .setPositiveButton("올리기") { _, _ ->
                prefs.edit().putString(KEY_OPT, "in").apply()
                syncThenLoad()
            }
            .setNegativeButton("보기만") { _, _ ->
                prefs.edit().putString(KEY_OPT, "out").apply()
                showTab()
            }
            .setCancelable(false)
            .show()
    }

    /** 올리기로 한 사람: 스테이지 기록을 올리고 온라인 순위표에도 보이게 한 뒤 보여 준다. */
    private fun syncThenLoad() {
        val me = uid ?: return
        val avatar = Wallet.selectedAvatarId(this)
        Ranking.setOnlineShown(me, true, avatar)
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "내 기록을 올리는 중…"
        Ranking.syncMine(me, Wallet.bestStage(this), avatar) { onServer ->
            if (isFinishing || isDestroyed) return@syncMine
            stageOnServer = onServer
            showTab()
        }
    }

    /** 서버에 올라가 있는 내 스테이지. 규칙이 한 번에 올려 주는 만큼만 올라가 로컬보다 낮을 수 있다. */
    private var stageOnServer: Int? = null

    private fun showTab() {
        renderHeader()
        load()
    }

    private fun renderHeader() {
        binding.btnRemove.text =
            if (optedIn(this)) "순위표에서 내 기록 내리기" else "순위표에 내 기록 올리기"
        binding.tvMineNote.visibility = View.GONE
        when (tab) {
            Tab.STAGE -> {
                binding.tvSubtitle.text = "스테이지 도전에서 가장 멀리 간 스테이지로 순위를 매겨요"
                val best = Wallet.bestStage(this)
                // 1스테이지는 아직 하나도 못 깬 상태다. "1스테이지" 라고 쓰면 깬 것처럼 읽힌다.
                binding.tvMine.text = if (best < 2) "아직 깬 스테이지가 없어요" else "${best}스테이지"
                val onServer = stageOnServer
                if (optedIn(this) && onServer != null && onServer < best && best >= 2) {
                    binding.tvMineNote.visibility = View.VISIBLE
                    binding.tvMineNote.text = "순위표에는 ${onServer}스테이지로 올라가 있어요. 시간이 지나며 따라 올라가요"
                }
            }
            Tab.ONLINE -> {
                binding.tvSubtitle.text = "랜덤 매칭에서 이긴 횟수로 순위를 매겨요 · 친구 방과 너무 빨리 끝난 판은 세지 않아요"
                binding.tvMine.text = "불러오는 중…"
                val me = uid ?: return
                Ranking.onlineMine(me) { mine ->
                    if (isFinishing || isDestroyed || tab != Tab.ONLINE) return@onlineMine
                    binding.tvMine.text =
                        if (mine == null || mine.wins + mine.losses == 0) "아직 센 판이 없어요"
                        else "${mine.wins}승 ${mine.losses}패"
                    binding.tvMineNote.visibility = View.VISIBLE
                    binding.tvMineNote.text =
                        if (optedIn(this)) "광고로 패배를 지워도 순위 기록은 그대로예요"
                        else "순위표에는 안 보이는 중이에요"
                }
            }
        }
    }

    private fun load() {
        val requested = tab
        binding.list.removeAllViews()
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "불러오는 중…"
        binding.btnRemove.visibility = View.VISIBLE
        val onError = { msg: String ->
            if (!isFinishing && !isDestroyed && tab == requested) binding.tvStatus.text = msg
        }
        when (requested) {
            Tab.STAGE -> Ranking.top(
                TOP,
                onResult = { list ->
                    if (isFinishing || isDestroyed || tab != requested) return@top
                    render(list.map { Row(it.uid, it.avatar, "${it.stage}스테이지", it.stage.toLong()) })
                },
                onError = onError
            )
            Tab.ONLINE -> Ranking.onlineTop(
                ONLINE_READ,
                onResult = { list ->
                    if (isFinishing || isDestroyed || tab != requested) return@onlineTop
                    render(
                        OnlineRules.onlineLeaderboard(list, TOP).map {
                            // 같은 승수·같은 패수면 같은 순위
                            Row(it.uid, it.avatar, "${it.wins}승 ${it.losses}패", it.wins * 100_000L - it.losses)
                        }
                    )
                },
                onError = onError
            )
        }
    }

    /** 순위표 한 줄. [score] 가 같으면 같은 순위로 매긴다. */
    private class Row(val uid: String, val avatar: String?, val right: String, val score: Long)

    private fun render(rows: List<Row>) {
        binding.list.removeAllViews()
        binding.tvStatus.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        binding.tvStatus.text = "아직 순위표가 비어 있어요. 첫 번째가 되어 보세요!"
        val me = uid
        var rank = 0
        var prev: Long? = null
        rows.forEachIndexed { i, r ->
            if (r.score != prev) rank = i + 1
            prev = r.score
            binding.list.addView(row(rank, r, isMe = r.uid == me))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun row(rank: Int, r: Row, isMe: Boolean): View {
        val def = AvatarCatalog.byIdOrDefault(r.avatar ?: "")
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
            text = r.right
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@RankingActivity, R.color.accent2))
        })
        return row
    }

    private fun toggleOpt() {
        val me = uid ?: return
        val avatar = Wallet.selectedAvatarId(this)
        if (optedIn(this)) {
            prefs.edit().putString(KEY_OPT, "out").apply()
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = "내리는 중…"
            stageOnServer = null
            Ranking.setOnlineShown(me, false, avatar)
            Ranking.removeMine(me) { if (!isFinishing && !isDestroyed) showTab() }
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
