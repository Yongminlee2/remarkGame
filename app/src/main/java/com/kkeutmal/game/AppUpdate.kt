package com.kkeutmal.game

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * 새 버전이 나오면 앱 안에서 받게 한다 (Play 인앱 업데이트).
 *
 * **유연(FLEXIBLE) 방식**을 쓴다. 강제(IMMEDIATE)는 업데이트를 마칠 때까지 앱을
 * 못 쓰게 막는데, 단어 게임에 그렇게까지 할 이유가 없다. 유연 방식은 내려받는 동안
 * 그대로 놀 수 있고, 다 받으면 "설치할까요?"만 물어본다.
 *
 * 스토어에서 받은 앱에서만 동작한다. adb 로 설치한 빌드나 에뮬레이터에서는
 * 조용히 아무 일도 일어나지 않는다 — 실기기 검증이 필요한 이유다.
 */
object AppUpdate {

    /**
     * 새 버전이 있는지 보고, 있으면 내려받기를 권한다.
     * 화면에 들어올 때마다 불러도 된다 — 이미 받아 둔 게 있으면 설치만 다시 권한다.
     */
    fun check(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val manager = AppUpdateManagerFactory.create(activity)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                // 지난번에 받아 두고 설치는 안 한 경우. 새로 받을 것 없이 설치만 권한다.
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    promptInstall(activity, manager)
                    return@addOnSuccessListener
                }
                val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                if (available && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    manager.registerListener(onDownloaded(activity, manager))
                    runCatching {
                        manager.startUpdateFlowForResult(
                            info,
                            launcher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    }
                }
            }
            // 스토어를 통하지 않은 빌드에서는 실패한다. 게임과 무관하니 조용히 넘어간다.
            .addOnFailureListener { }
    }

    private fun onDownloaded(activity: Activity, manager: AppUpdateManager) =
        object : InstallStateUpdatedListener {
            override fun onStateUpdate(state: com.google.android.play.core.install.InstallState) {
                if (state.installStatus() == InstallStatus.DOWNLOADED) {
                    manager.unregisterListener(this)
                    promptInstall(activity, manager)
                }
            }
        }

    /**
     * 다 받았다고 알리고 설치를 맡긴다.
     *
     * 대화상자 대신 스낵바를 쓴다. 켤 때마다 창이 뜨면 성가시고, 설치를 미루는 것도
     * 사용자의 선택이라 화면을 막지 않는다.
     */
    private fun promptInstall(activity: Activity, manager: AppUpdateManager) {
        val root = activity.findViewById<android.view.View>(android.R.id.content) ?: return
        Snackbar.make(root, "새 버전을 받았어요", Snackbar.LENGTH_INDEFINITE)
            .setAction("설치") { manager.completeUpdate() }
            .show()
    }
}
