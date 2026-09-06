package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 메인 화면 전적 칸에 띄우는 승률.
 *
 * **한 판도 안 한 사람에서 0 으로 나누기가 난다.** 그런데 개발자는 이미 몇 판 해 본
 * 상태라 앱을 다시 깔지 않는 한 재현되지 않는다 — 첫 사용자만 겪는 종류의 사고다.
 * 그래서 화면이 아니라 여기서 굳혀 둔다.
 */
class WinRateTest {

    @Test
    fun `한 판도 안 했으면 null 이라 화면에서 감춘다`() {
        assertNull(Wallet.winRatePercent(0, 0))
    }

    @Test
    fun `이긴 비율을 퍼센트로 돌려준다`() {
        assertEquals(75, Wallet.winRatePercent(3, 1))
        assertEquals(50, Wallet.winRatePercent(1, 1))
    }

    @Test
    fun `전승과 전패도 0으로 나누지 않는다`() {
        assertEquals(100, Wallet.winRatePercent(5, 0))
        assertEquals(0, Wallet.winRatePercent(0, 5))
    }

    @Test
    fun `내림이라 100퍼센트는 전승일 때만 나온다`() {
        // 반올림했다면 99승 1패가 100% 가 되어 전승과 구분이 안 된다.
        assertEquals(99, Wallet.winRatePercent(99, 1))
    }

    @Test
    fun `음수가 섞여도 0으로 나누지 않는다`() {
        assertNull(Wallet.winRatePercent(-2, 2))
    }
}
