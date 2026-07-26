package com.kkeutmal.game

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kkeutmal.game.databinding.ActivityCollectionBinding

class CollectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        binding.btnBack.setOnClickListener { finish() }
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun gradeColor(grade: AvatarGrade) = ContextCompat.getColor(
        this,
        when (grade) {
            AvatarGrade.COMMON -> R.color.grade_common
            AvatarGrade.RARE -> R.color.grade_rare
            AvatarGrade.EPIC -> R.color.grade_epic
            AvatarGrade.LEGENDARY -> R.color.grade_legendary
        }
    )

    private fun render() {
        val owned = Wallet.ownedAvatarIds(this)
        val selected = Wallet.selectedAvatarId(this)
        binding.tvCollected.text = "${owned.size} / ${AvatarCatalog.ALL.size} 수집"

        binding.grid.removeAllViews()
        for (def in AvatarCatalog.ALL) {
            val isOwned = def.id in owned
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(8), dp(6), dp(8))
                background = (ContextCompat.getDrawable(
                    this@CollectionActivity, R.drawable.bg_grade_border
                ) as GradientDrawable).apply {
                    mutate()
                    setStroke(dp(if (def.id == selected) 3 else 2), gradeColor(def.grade))
                }
            }
            cell.addView(AvatarView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                bind(def, locked = !isOwned)
            })
            cell.addView(TextView(this).apply {
                text = when {
                    def.id == selected -> "사용 중"
                    isOwned -> def.grade.label
                    else -> "잠김"
                }
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(
                    if (def.id == selected) ContextCompat.getColor(this@CollectionActivity, R.color.accent2)
                    else gradeColor(def.grade)
                )
            })
            cell.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            cell.setOnClickListener { onTap(def, isOwned) }
            binding.grid.addView(cell)
        }
    }

    private fun onTap(def: AvatarDef, isOwned: Boolean) {
        if (isOwned) {
            Wallet.selectAvatarId(this, def.id)
            render()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(def.name)
            .setMessage("${def.grade.label} 등급\n획득 방법: ${AvatarCatalog.unlockDescription(def)}")
            .setPositiveButton("확인", null)
            .show()
    }
}
