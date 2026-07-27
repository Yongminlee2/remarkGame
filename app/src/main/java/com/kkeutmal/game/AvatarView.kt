package com.kkeutmal.game

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat

/**
 * 몸통 · 눈(좌우 2개) · 입 PNG 를 겹쳐 아바타 하나를 그린다.
 * 잠긴 아바타는 진회청색(#40465F) 실루엣으로 표시한다.
 *
 * 몸통 PNG는 80x80(뷰 비율과 동일)이라 MATCH_PARENT/FIT_CENTER로 충분하지만,
 * 눈·입 PNG는 꽉 채운 바운딩박스(예: 눈 20x20, 화난 입 40x10)라서 MATCH_PARENT로
 * 두면 각 레이어가 뷰 전체 크기로 따로 늘어나 서로 다른 비율로 찌그러진다.
 * 게다가 눈 에셋(facial_part_eye_*)은 눈 한 짝만 담고 있으므로 좌우 대칭으로
 * 두 번 그려야 한다. 그래서 눈·입은 onSizeChanged/바인딩 시점에 intrinsic 비율을
 * 유지한 채 명시적으로 크기와 위치를 잡아준다.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val body = ImageView(context)
    private val browLeft = ImageView(context)
    private val browRight = ImageView(context)
    private val eyeLeft = ImageView(context)
    private val eyeRight = ImageView(context)
    private val mouth = ImageView(context)

    private val faceParts = listOf(browLeft, browRight, eyeLeft, eyeRight, mouth)
    private val allLayers = listOf(body, browLeft, browRight, eyeLeft, eyeRight, mouth)

    init {
        body.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        faceParts.forEach {
            it.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        // 눈·눈썹 에셋은 한 짝짜리라 오른쪽은 좌우 반전해서 대칭으로 보이게 한다
        eyeRight.scaleX = -1f
        browRight.scaleX = -1f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutFaceParts()
    }

    fun bind(def: AvatarDef?, locked: Boolean = false) {
        if (def == null) {
            allLayers.forEach {
                it.setImageDrawable(null)
                it.clearColorFilter()
            }
            alpha = 1f
            return
        }
        body.setImageResource(drawableId(def.bodyAsset))
        eyeLeft.setImageResource(drawableId(def.face.eyeAsset))
        eyeRight.setImageResource(drawableId(def.face.eyeAsset))
        mouth.setImageResource(drawableId(def.face.mouthAsset))

        // 눈썹은 표정에 따라 있을 수도 없을 수도 있다
        val brow = def.face.browAsset
        if (brow == null) {
            browLeft.setImageDrawable(null)
            browRight.setImageDrawable(null)
        } else {
            browLeft.setImageResource(drawableId(brow))
            browRight.setImageResource(drawableId(brow))
        }

        if (locked) {
            val silhouette = ContextCompat.getColor(context, R.color.locked_silhouette)
            allLayers.forEach { it.setColorFilter(silhouette) }
            alpha = 1f
        } else {
            allLayers.forEach { it.clearColorFilter() }
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

    /** 눈(좌우)·입 레이어를 몸통 위 제자리에, intrinsic 비율을 유지한 채 배치한다. */
    private fun layoutFaceParts() {
        if (width <= 0 || height <= 0) return

        val eyeWidth = width * 0.16f
        val eyeTopMargin = height * 0.34f
        sizeFacePart(eyeLeft, eyeWidth, eyeTopMargin, leftMargin = width * 0.26f)
        sizeFacePart(eyeRight, eyeWidth, eyeTopMargin, leftMargin = width * 0.58f)

        // 눈썹은 눈 바로 위(0.24~0.30). 눈 상단 0.34 와 겹치지 않는다.
        val browTopMargin = height * 0.24f
        sizeFacePart(browLeft, eyeWidth, browTopMargin, leftMargin = width * 0.26f)
        sizeFacePart(browRight, eyeWidth, browTopMargin, leftMargin = width * 0.58f)

        sizeFacePart(mouth, width * 0.24f, height * 0.60f, centerHorizontal = true)
    }

    private fun sizeFacePart(
        view: ImageView,
        targetWidth: Float,
        topMargin: Float,
        leftMargin: Float = 0f,
        centerHorizontal: Boolean = false
    ) {
        val drawable = view.drawable
        val ratio = if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
        } else {
            1f // 드로어블이 없거나 크기를 알 수 없으면 정사각형으로 폴백
        }
        val targetHeight = targetWidth / ratio
        view.layoutParams = LayoutParams(targetWidth.toInt(), targetHeight.toInt()).apply {
            this.topMargin = topMargin.toInt()
            if (centerHorizontal) {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            } else {
                gravity = Gravity.TOP or Gravity.START
                this.leftMargin = leftMargin.toInt()
            }
        }
    }

    private fun drawableId(name: String): Int =
        resources.getIdentifier(name, "drawable", context.packageName)
            .also { require(it != 0) { "드로어블을 찾을 수 없음: $name" } }
}
