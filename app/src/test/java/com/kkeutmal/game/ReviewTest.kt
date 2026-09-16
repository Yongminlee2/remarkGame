package com.kkeutmal.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 리뷰 창을 언제 띄우는가.
 *
 * 너무 일찍 물으면 해 보지도 않은 사람이 별점을 매기고, 여러 번 물으면 성가셔서 낮은 별점이
 * 나온다. 둘 다 화면에서는 티가 안 나고 스토어 평점으로만 돌아온다.
 */
class ReviewTest {

    @Test
    fun `몇 판 이겨 보기 전에는 묻지 않는다`() {
        assertFalse(Review.shouldAsk(wins = 0, alreadyAsked = false))
        assertFalse(Review.shouldAsk(wins = 2, alreadyAsked = false))
    }

    @Test
    fun `충분히 이기면 묻는다`() {
        assertTrue(Review.shouldAsk(wins = 3, alreadyAsked = false))
        assertTrue(Review.shouldAsk(wins = 50, alreadyAsked = false))
    }

    @Test
    fun `한 번 물었으면 다시 묻지 않는다`() {
        assertFalse(Review.shouldAsk(wins = 3, alreadyAsked = true))
        assertFalse(Review.shouldAsk(wins = 500, alreadyAsked = true))
    }
}
