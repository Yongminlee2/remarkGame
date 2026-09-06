package com.kkeutmal.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 릴리스에 나가는 광고 단위 ID 를 지킨다.
 *
 * 개발 중에는 구글 공개 테스트 단위를 쓴다 — 자기 앱의 진짜 광고를 자기가 누르면
 * 부정 클릭으로 AdMob 계정이 정지될 수 있어서다. 그래서 코드 안에 테스트 값과 실제 값이
 * 나란히 있는데, **테스트 값이 실제 자리에 남아도 앱은 멀쩡히 돌아간다.** 광고까지
 * 잘 나온다. 다만 수익이 0 원일 뿐이라 **아무도 몇 달간 눈치채지 못할 수 있다.**
 *
 * 눈으로는 두 값이 똑같이 생긴 긴 숫자라 구분이 안 된다. 그래서 여기서 굳혀 둔다.
 */
class AdUnitIdTest {

    /** 구글이 아무나 쓰라고 공개한 계정. 우리 릴리스에 이게 있으면 안 된다. */
    private val googleTestAccount = "ca-app-pub-3940256099942544"

    /** 우리 AdMob 게시자 계정. app-ads.txt 에 적어 둔 것과 같은 번호다. */
    private val ourAccount = "ca-app-pub-6583185616347720"

    private val releaseUnits = mapOf(
        "배너" to Ads.REAL_BANNER,
        "보상형" to Ads.REAL_REWARDED
    )

    @Test
    fun `릴리스 광고 단위에 구글 테스트 계정이 남아 있지 않다`() {
        for ((name, id) in releaseUnits) {
            assertFalse(
                "$name 이 아직 구글 테스트 단위다. 이대로 내면 광고는 나오지만 수익이 0 원이다: $id",
                id.startsWith(googleTestAccount)
            )
        }
    }

    @Test
    fun `릴리스 광고 단위는 우리 계정의 것이다`() {
        for ((name, id) in releaseUnits) {
            assertTrue(
                "$name 이 우리 계정($ourAccount)의 단위가 아니다: $id",
                id.startsWith("$ourAccount/")
            )
            // 계정 뒤에 단위 번호가 실제로 붙어 있어야 한다
            assertTrue("$name 에 단위 번호가 없다: $id", id.substringAfter('/').length >= 6)
        }
    }

    @Test
    fun `배너와 보상형은 서로 다른 단위다`() {
        // 복사해 붙이다 한쪽만 바꾸는 실수. 형식이 멀쩡해 위 두 검사를 그냥 통과한다.
        assertNotEquals(
            "배너와 보상형이 같은 광고 단위를 쓰고 있다",
            Ads.REAL_BANNER,
            Ads.REAL_REWARDED
        )
    }
}
