package com.kkeutmal.game

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * 광고를 한 곳에서 다룬다 — 광고 단위 ID, SDK 시작, EU 동의, 보상형 광고.
 *
 * **개발 중에는 반드시 테스트 광고만 띄운다.** 자기 앱의 진짜 광고를 자기가 누르면
 * 부정 클릭으로 잡혀 AdMob 계정이 정지될 수 있다. 그래서 디버그 빌드는 구글이 공개한
 * 테스트 광고 단위를 쓰고, 릴리스 빌드만 실제 단위를 쓴다.
 */
object Ads {

    // 구글 공개 테스트 광고 단위. 아무 계정에서나 쓸 수 있고 수익은 나지 않는다.
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    // TODO: AdMob 에서 만든 실제 광고 단위 ID 로 바꾼다 (ca-app-pub-6583185616347720/숫자).
    //       바꾸지 않으면 **릴리스에서도 테스트 광고가 나가 수익이 0 원이다.**
    private const val REAL_BANNER = TEST_BANNER
    private const val REAL_REWARDED = TEST_REWARDED

    val bannerUnitId: String get() = if (BuildConfig.DEBUG) TEST_BANNER else REAL_BANNER
    private val rewardedUnitId: String get() = if (BuildConfig.DEBUG) TEST_REWARDED else REAL_REWARDED

    @Volatile
    private var started = false

    private var rewarded: RewardedAd? = null

    /**
     * 동의를 확인한 뒤 광고 SDK 를 시작한다. 홈 화면에서 한 번 부르면 된다.
     *
     * EEA(유럽) 사용자에게는 **광고를 띄우기 전에 동의를 받는 것이 법적 의무**다.
     * 이 앱은 177개국에 배포되므로 유럽 사용자가 실제로 들어온다. UMP 가 지역을 보고
     * 필요한 곳에서만 동의 창을 띄우므로, 한국 사용자에게는 아무것도 보이지 않는다.
     */
    fun start(activity: Activity) {
        val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consent.canRequestAds()) startSdk(activity)
                }
            },
            {
                // 동의 정보를 못 받아도 게임은 그대로 돌아야 한다. 광고만 포기한다.
            }
        )
        // 이미 동의를 받아 둔 사용자(또는 동의가 필요 없는 지역)는 기다릴 것 없이 바로 시작한다.
        if (consent.canRequestAds()) startSdk(activity)
    }

    private fun startSdk(ctx: Context) {
        if (started) return
        started = true
        MobileAds.initialize(ctx) { }
        loadRewarded(ctx)
    }

    fun request(): AdRequest = AdRequest.Builder().build()

    // ---------- 보상형 ----------

    /** 미리 받아 둔다. 게임이 끝난 뒤에 받기 시작하면 기다리는 시간이 그대로 노출된다. */
    fun loadRewarded(ctx: Context) {
        if (!started || rewarded != null) return
        RewardedAd.load(
            ctx, rewardedUnitId, request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                }
            }
        )
    }

    /** 지금 당장 보여 줄 수 있는가. false 면 부르는 쪽에서 광고 얘기를 꺼내지 않는다. */
    fun isRewardedReady(): Boolean = rewarded != null

    /**
     * 보상형 광고를 보여 준다.
     *
     * @param onClosed 광고가 닫힌 뒤 한 번 불린다. earned 가 true 일 때만 보상을 줄 것 —
     *   중간에 닫으면 false 다. **이 값을 안 보고 보상을 주면 광고를 안 봐도 받게 된다.**
     */
    fun showRewarded(activity: Activity, onClosed: (earned: Boolean) -> Unit) {
        val ad = rewarded
        if (ad == null) {
            onClosed(false)
            return
        }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded(activity) // 다음 판을 위해 미리 받아 둔다
                onClosed(earned)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                loadRewarded(activity)
                onClosed(false)
            }
        }
        ad.show(activity) { earned = true }
    }
}
