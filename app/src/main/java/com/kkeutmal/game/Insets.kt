package com.kkeutmal.game

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 시스템 바(상태바·내비게이션바)와 키보드 높이만큼 루트 뷰에 패딩을 준다.
 *
 * targetSdk 35(안드로이드 15)부터 시스템이 edge-to-edge 를 강제한다.
 * 앱이 상태바·내비게이션바 영역까지 그리게 되고, 테마의
 * android:statusBarColor / android:navigationBarColor 는 무시된다.
 * 이 처리가 없으면 화면 맨 위와 맨 아래가 시스템 바에 가린다.
 *
 * @param includeIme 키보드가 뜰 때 입력창이 가리면 안 되는 화면(게임)에서 true
 */
fun View.applySystemBarInsets(includeIme: Boolean = false) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
        val mask = if (includeIme) {
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        } else {
            WindowInsetsCompat.Type.systemBars()
        }
        val bars = windowInsets.getInsets(mask)
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        windowInsets
    }
}
