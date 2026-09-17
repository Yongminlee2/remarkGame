package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 온라인 대전에서 상대의 "쓰는 중" 글자를 사전 낱말의 앞부분까지만 보여 주는지.
 * 이게 뚫리면 사전에 없는 욕설을 상대 화면에 띄울 수 있다.
 */
class TypingPreviewTest {

    companion object {
        private val assets = File("src/main/assets")

        @BeforeClass
        @JvmStatic
        fun loadDict() {
            if (assets.isDirectory) WordDict.loadWordsForTest(assets)
        }
    }

    private fun requireDict() =
        assumeTrue("사전 자산을 못 찾음: ${assets.absolutePath}", WordDict.ready)

    @Test
    fun `사전 낱말의 앞부분은 그대로 보인다`() {
        requireDict()
        assertEquals("사과", WordDict.dictPrefixOf("사과"))
        assertEquals("사과나", WordDict.dictPrefixOf("사과나")) // 사과나무
    }

    @Test
    fun `사전에서 벗어난 뒷부분은 잘린다`() {
        requireDict()
        assertEquals("사과", WordDict.dictPrefixOf("사과ㅋㅋㅋ"))
        assertEquals("사과", WordDict.dictPrefixOf("사과abc"))
    }

    @Test
    fun `사전 낱말로 시작하지 않는 글은 아무것도 안 보인다`() {
        requireDict()
        assertEquals("", WordDict.dictPrefixOf("ㅅㅂ"))
        assertEquals("", WordDict.dictPrefixOf("fuck"))
        assertEquals("", WordDict.dictPrefixOf("   "))
    }
}
