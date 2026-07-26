package com.kkeutmal.game

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * 몸통 · 눈 · 입 PNG 를 겹쳐 아바타 하나를 그린다.
 * 잠긴 아바타는 검은 실루엣으로 표시한다.
 *
 * 몸통 PNG는 80x80(뷰 비율과 동일)이라 MATCH_PARENT/FIT_CENTER로 충분하지만,
 * 눈·입 PNG는 꽉 채운 바운딩박스(예: 눈 20x20, 화난 입 40x10)라서 MATCH_PARENT로
 * 두면 각 레이어가 뷰 전체 크기로 따로 늘어나 서로 다른 비율로 찌그러진다.
 * 그래서 눈·입은 onSizeChanged/바인딩 시점에 intrinsic 비율을 유지한 채
 * 명시적으로 크기와 위치를 잡아준다.
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
        body.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        listOf(eyes, mouth).forEach {
            it.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutFaceParts()
    }

    fun bind(def: AvatarDef?, locked: Boolean = false) {
        if (def == null) {
            listOf(body, eyes, mouth).forEach {
                it.setImageDrawable(null)
                it.clearColorFilter()
            }
            alpha = 1f
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

        if (width > 0) layoutFaceParts()
    }

    /** 보스용: 화난 입으로 바꾸고 크게 보이게 한다. */
    fun bindBoss(def: AvatarDef) {
        bind(def)
        mouth.setImageResource(drawableId("facial_part_mouth_angry"))
        if (width > 0) layoutFaceParts()
    }

    /** 눈·입 레이어를 몸통 위 제자리에, intrinsic 비율을 유지한 채 배치한다. */
    private fun layoutFaceParts() {
        if (width <= 0 || height <= 0) return
        sizeFacePart(eyes, width * 0.30f, height * 0.34f)
        sizeFacePart(mouth, width * 0.26f, height * 0.56f)
    }

    private fun sizeFacePart(view: ImageView, targetWidth: Float, topMargin: Float) {
        val drawable = view.drawable
        val ratio = if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
        } else {
            1f // 드로어블이 없거나 크기를 알 수 없으면 정사각형으로 폴백
        }
        val targetHeight = targetWidth / ratio
        view.layoutParams = LayoutParams(targetWidth.toInt(), targetHeight.toInt()).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            this.topMargin = topMargin.toInt()
        }
    }

    private fun drawableId(name: String): Int =
        resources.getIdentifier(name, "drawable", context.packageName)
            .also { require(it != 0) { "드로어블을 찾을 수 없음: $name" } }
}
