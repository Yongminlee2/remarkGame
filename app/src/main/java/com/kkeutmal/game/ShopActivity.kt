package com.kkeutmal.game

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kkeutmal.game.databinding.ActivityShopBinding

class ShopActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShopBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShopBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        Ads.attachBanner(this, binding.adContainer)
        binding.btnBack.setOnClickListener { finish() }
        refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun refresh() {
        binding.tvShopCoins.text = "🪙 ${Wallet.coins(this)}"
        buildAvatars()
        buildItems()
    }

    private fun buildAvatars() {
        val grid = binding.gridAvatars
        grid.removeAllViews()
        val owned = Wallet.ownedAvatarIds(this)
        val selected = Wallet.selectedAvatarId(this)

        for (def in AvatarCatalog.ALL.filter { it.grade == AvatarGrade.COMMON }) {
            val isOwned = def.id in owned
            val price = (def.unlock as Unlock.Coin).price
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6))
                background = ContextCompat.getDrawable(this@ShopActivity,
                    if (def.id == selected) R.drawable.bg_cell_selected else R.drawable.bg_input)
            }
            cell.addView(AvatarView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                bind(def, locked = !isOwned)
            })
            cell.addView(TextView(this).apply {
                text = when {
                    def.id == selected -> "사용 중"
                    isOwned -> "보유"
                    else -> "${price}🪙"
                }
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@ShopActivity,
                    if (def.id == selected) R.color.accent2 else R.color.text_dim))
            })
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            cell.layoutParams = lp
            cell.setOnClickListener { onAvatarTap(def, isOwned) }
            grid.addView(cell)
        }
    }

    private fun onAvatarTap(def: AvatarDef, owned: Boolean) {
        val price = (def.unlock as Unlock.Coin).price
        if (owned) {
            Wallet.selectAvatarId(this, def.id)
            refresh()
            return
        }
        if (Wallet.coins(this) < price) {
            MaterialAlertDialogBuilder(this)
                .setMessage("코인이 부족해요 (${price}🪙 필요)")
                .setPositiveButton("확인", null).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("${def.name} 구매")
            .setMessage("${price}🪙 로 구매할까요?")
            .setPositiveButton("구매") { _, _ ->
                if (Wallet.spendCoins(this, price)) {
                    Wallet.ownAvatarId(this, def.id)
                    Wallet.selectAvatarId(this, def.id)
                    refresh()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun buildItems() {
        val list = binding.listItems
        list.removeAllViews()
        for (item in Wallet.ITEMS) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = ContextCompat.getDrawable(this@ShopActivity, R.drawable.bg_input)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(4), 0, dp(4)) }
            }
            row.addView(TextView(this).apply { text = item.emoji; textSize = 24f })
            val mid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setMargins(dp(12), 0, dp(8), 0) }
            }
            mid.addView(TextView(this).apply {
                text = "${item.nameLabel}  (보유 ${Wallet.itemCount(this@ShopActivity, item.id)})"
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@ShopActivity, R.color.text_primary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            mid.addView(TextView(this).apply {
                text = item.descLabel
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@ShopActivity, R.color.text_dim))
            })
            row.addView(mid)
            row.addView(MaterialButton(this).apply {
                text = "${item.price}🪙"
                textSize = 13f
                cornerRadius = dp(18)
                setTextColor(Color.WHITE)
                backgroundTintList = ContextCompat.getColorStateList(this@ShopActivity, R.color.accent)
                setOnClickListener { buyItem(item) }
            })
            list.addView(row)
        }
    }

    private fun buyItem(item: Wallet.Item) {
        if (Wallet.coins(this) < item.price) {
            MaterialAlertDialogBuilder(this)
                .setMessage("코인이 부족해요 (${item.price}🪙 필요)")
                .setPositiveButton("확인", null).show()
            return
        }
        if (Wallet.spendCoins(this, item.price)) {
            Wallet.addItem(this, item.id, 1)
            refresh()
        }
    }
}
