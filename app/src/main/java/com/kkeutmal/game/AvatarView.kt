package com.kkeutmal.game

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * 몸통 · 눈 · 입 PNG 를 겹쳐 아바타 하나를 그린다.
 * 잠긴 아바타는 검은 실루엣으로 표시한다.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val body = ImageView(context)
    private val eyes = ImageView(context)
    private val mouth = ImageView(context)

    init {
        listOf(body, eyes, mouth).forEach {
            it.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    fun bind(def: AvatarDef?, locked: Boolean = false) {
        if (def == null) {
            listOf(body, eyes, mouth).forEach { it.setImageDrawable(null) }
            return
        }
        body.setImageResource(drawableId(def.bodyAsset))
        eyes.setImageResource(drawableId(def.face.eyeAsset))
        mouth.setImageResource(drawableId(def.face.mouthAsset))

        if (locked) {
            body.setColorFilter(Color.BLACK)
            eyes.setColorFilter(Color.BLACK)
            mouth.setColorFilter(Color.BLACK)
            alpha = 0.45f
        } else {
            listOf(body, eyes, mouth).forEach { it.clearColorFilter() }
            alpha = 1f
        }
    }

    /** 보스용: 화난 입으로 바꾸고 크게 보이게 한다. */
    fun bindBoss(def: AvatarDef) {
        bind(def)
        mouth.setImageResource(drawableId("facial_part_mouth_angry"))
    }

    private fun drawableId(name: String): Int =
        resources.getIdentifier(name, "drawable", context.packageName)
            .also { require(it != 0) { "드로어블을 찾을 수 없음: $name" } }
}
