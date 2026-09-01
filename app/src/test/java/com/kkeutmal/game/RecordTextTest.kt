package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 메인 화면에 뜨는 승패 전적 문장.
 *
 * 승률은 정수 나눗셈이라 **한 판도 안 한 사람에서 0으로 나누기가 난다.** 그 경우
 * null 을 돌려 줄째로 감추기로 했는데, 나중에 "빈 화면이 허전하다"며 0승 0패를
 * 띄우려다 앱이 죽는 일이 없도록 여기서 굳혀 둔다.
 */
class RecordTextTest {

    @Test
    fun `한 판도 안 했으면 null 이라 화면에서 감춘다`() {
        assertNull(Wallet.recordText(0, 0))
    }

    @Test
    fun `승과 패를 그대로 적고 승률을 붙인다`() {
        assertEquals("3승 1패 · 승률 75%", Wallet.recordText(3, 1))
        assertEquals("1승 1패 · 승률 50%", Wallet.recordText(1, 1))
    }

    @Test
    fun `전승과 전패도 0으로 나누지 않는다`() {
        assertEquals("5승 0패 · 승률 100%", Wallet.recordText(5, 0))
        assertEquals("0승 5패 · 승률 0%", Wallet.recordText(0, 5))
    }

    @Test
    fun `승률은 내림이라 100퍼센트는 전승일 때만 나온다`() {
        // 99승 1패는 99% 여야 한다. 반올림했다면 100% 가 되어 전승과 구분이 안 된다.
        assertEquals("99승 1패 · 승률 99%", Wallet.recordText(99, 1))
    }
}
