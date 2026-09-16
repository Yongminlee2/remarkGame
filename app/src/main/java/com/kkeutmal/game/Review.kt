package com.kkeutmal.game

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * 플레이 스토어 리뷰 창(앱을 떠나지 않고 별점을 남기는 구글 공식 창).
 *
 * **리뷰에 보상을 주지 않는다.** 구글 정책이 "별점을 주면 아이템을 준다" 를 금지 사례로
 * 직접 들고 있어, 걸리면 앱이 내려갈 수 있다. 창을 띄우기 전에 "재밌으세요?" 같은 질문을
 * 먼저 묻는 것도 금지다 — 그래서 아무 말 없이 바로 띄운다.
 *
 * 구글이 창 노출 횟수를 스스로 제한하므로 요청해도 안 뜰 수 있다. 결과(리뷰를 남겼는지)는
 * 앱에 알려 주지 않는다 — 보상을 주고 싶어도 기술적으로 줄 방법이 없는 구조다.
 */
object Review {

    /** 몇 번 이긴 뒤에 물어볼지. 충분히 해 본 사람에게 물어야 쓸모 있는 리뷰가 나온다. */
    private const val WINS_BEFORE_ASKING = 3

    private const val KEY_ASKED = "review_asked"

    /** 저장소를 안 타는 판단부. 테스트로 묶어 둔다. */
    fun shouldAsk(wins: Int, alreadyAsked: Boolean): Boolean =
        !alreadyAsked && wins >= WINS_BEFORE_ASKING

    /**
     * 조건이 맞으면 리뷰 창을 한 번 띄운다.
     *
     * 띄우기 **전에** 물어봤다고 적는다. 구글이 창을 안 보여 줘도 다시 조르지 않기 위해서다 —
     * 자꾸 묻는 것 자체가 정책상 권장되지 않는다.
     */
    fun maybeAsk(activity: Activity) {
        val prefs = activity.getSharedPreferences("kkeutmal", Context.MODE_PRIVATE)
        if (!shouldAsk(Wallet.wins(activity), prefs.getBoolean(KEY_ASKED, false))) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnSuccessListener { info ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                manager.launchReviewFlow(activity, info)
            }
        }
    }
}
