package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 광고 하나를 보고 받는 아이템 개수.
 *
 * 개수를 하나로 고정하면 **비싼 아이템만 광고로 받는 것이 이득**이 되어 싼 아이템은
 * 아무도 광고로 받지 않는다. 그렇다고 아무 숫자나 넣으면 반대로 광고가 상점 구매를
 * 완전히 대체해 코인이 쓸모없어진다. 그 균형을 여기서 굳혀 둔다.
 */
class AdRewardCountTest {

    @Test
    fun `쌀수록 많이 준다`() {
        val time = Wallet.adRewardCount(60)      // 시간 +15초
        val hint = Wallet.adRewardCount(80)      // 힌트
        val revive = Wallet.adRewardCount(200)   // 부활
        assertTrue("시간($time) > 힌트($hint)", time > hint)
        assertTrue("힌트($hint) > 부활($revive)", hint > revive)
    }

    @Test
    fun `실제 상점 아이템은 모두 1개 이상 준다`() {
        for (item in Wallet.ITEMS) {
            val n = Wallet.adRewardCount(item.price)
            assertTrue("${item.nameLabel} 이 ${n}개", n >= 1)
        }
    }

    @Test
    fun `가장 비싼 아이템도 광고 한 편에 하나까지만`() {
        // 부활을 광고로 여러 개 받으면 게임 오버가 사실상 사라진다.
        assertEquals(1, Wallet.adRewardCount(200))
        assertEquals(1, Wallet.adRewardCount(9999))
    }

    @Test
    fun `광고가 상점을 완전히 대체하지는 않는다`() {
        // 광고 한 편으로 받는 코인 환산액이 아이템 값의 몇 배가 되면 코인이 쓸모없어진다.
        for (item in Wallet.ITEMS) {
            val worth = Wallet.adRewardCount(item.price) * item.price
            assertTrue("${item.nameLabel} 광고 한 편이 ${worth}코인 값어치", worth <= 240)
        }
    }
}
