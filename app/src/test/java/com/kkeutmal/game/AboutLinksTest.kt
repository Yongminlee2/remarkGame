package com.kkeutmal.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정보 화면이 내보내는 바깥 링크를 지킨다.
 *
 * **개인정보처리방침 링크가 죽으면 앱이 스토어에서 내려갈 수 있다.** 그래서 이 링크만은
 * 소스 저장소(remarkGame)를 가리키면 안 된다 — 그쪽은 언제든 private 으로 돌릴 수 있고,
 * 그 순간 404 가 된다. 방침·지원 문서는 계속 공개로 둘 legal 저장소에 따로 두었다.
 *
 * 코드를 읽는 사람에게는 둘 다 그냥 GitHub 주소로 보여서, 나중에 "정리"하다가
 * 한 곳으로 합치기 쉽다. 그걸 막으려고 테스트로 굳혀 둔다.
 *
 * const val 은 컴파일 때 인라인되므로 안드로이드 없이 단위 테스트에서 돌아간다.
 */
class AboutLinksTest {

    private val outward = mapOf(
        "앱 소개" to AboutActivity.DOCS,
        "개인정보처리방침" to AboutActivity.PRIVACY,
        "지원" to AboutActivity.SUPPORT
    )

    @Test
    fun `바깥 링크는 모두 https 이고 공백이 섞여 있지 않다`() {
        for ((name, url) in outward) {
            assertTrue("$name 링크가 https 가 아니다: $url", url.startsWith("https://"))
            assertTrue("$name 링크에 공백이 있다: $url", url == url.trim() && !url.contains(' '))
        }
    }

    @Test
    fun `바깥 링크는 private 이 될 수 있는 소스 저장소를 가리키지 않는다`() {
        for ((name, url) in outward) {
            assertFalse(
                "$name 링크가 소스 저장소를 가리킨다. remarkGame 이 private 이 되면 404 가 " +
                    "된다 — legal 저장소를 쓸 것: $url",
                url.contains("remarkGame", ignoreCase = true)
            )
            assertTrue(
                "$name 링크가 legal 저장소가 아니다: $url",
                url.startsWith("https://yongminlee2.github.io/legal/wordchain/")
            )
        }
    }

    @Test
    fun `세 링크는 서로 다른 문서를 가리킨다`() {
        assertTrue(
            "링크가 서로 겹친다: $outward",
            outward.values.toSet().size == outward.size
        )
    }
}
