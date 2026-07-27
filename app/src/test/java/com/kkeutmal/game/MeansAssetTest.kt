package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 뜻풀이 자산(means.bin / means.idx)이 사전과 아귀가 맞는지 본다.
 *
 * 이 셋은 서로를 전제한다 — dict_all.txt 의 i번째 단어 뜻이
 * means.bin[idx[i] until idx[i+1]] 이다. 한 파일만 다시 만들고 나머지를 안 맞추면
 * 모든 단어가 엉뚱한 뜻을 달게 되는데, 화면에서는 그냥 "이상한 뜻"으로 보여서
 * 알아채기 어렵다. 그래서 자산 자체를 테스트로 묶어 둔다.
 *
 * 안드로이드 없이 파일만 읽으므로 단위 테스트에서 돌아간다.
 */
class MeansAssetTest {

    private val assets = File("src/main/assets")

    private fun words(): List<String> =
        File(assets, "dict_all.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }

    private fun offsets(): IntArray {
        val b = File(assets, "means.idx").readBytes()
        return IntArray(b.size / 4) { i ->
            val p = i * 4
            (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
                ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)
        }
    }

    /** 테스트가 모듈 폴더가 아닌 곳에서 돌면 자산을 못 찾는다. 그때는 건너뛴다. */
    private fun requireAssets() =
        assumeTrue("자산 폴더를 못 찾음: ${assets.absolutePath}", assets.isDirectory)

    @Test
    fun `색인 항목 수는 단어 수보다 하나 많다`() {
        requireAssets()
        assertEquals(words().size + 1, offsets().size)
    }

    @Test
    fun `색인은 0에서 시작해 means bin 크기로 끝난다`() {
        requireAssets()
        val idx = offsets()
        assertEquals(0, idx.first())
        assertEquals(File(assets, "means.bin").length(), idx.last().toLong())
    }

    @Test
    fun `색인은 뒤로 가지 않는다`() {
        requireAssets()
        val idx = offsets()
        val broken = (0 until idx.size - 1).count { idx[it] > idx[it + 1] }
        assertEquals("오프셋이 역전된 항목", 0, broken)
    }

    @Test
    fun `뜻풀이가 비어 있는 단어는 없다`() {
        requireAssets()
        val idx = offsets()
        val list = words()
        val empty = (list.indices).filter { idx[it] == idx[it + 1] }
        assertTrue(
            "뜻 없는 단어 ${empty.size}개 (예: ${empty.take(5).map { list[it] }})",
            empty.isEmpty()
        )
    }

    @Test
    fun `모든 뜻풀이가 깨지지 않은 UTF-8 이다`() {
        requireAssets()
        val idx = offsets()
        val blob = File(assets, "means.bin").readBytes()
        val list = words()
        // 43만 건을 전부 디코드하면 느리니 고르게 흩어 뽑아 본다
        val step = maxOf(1, list.size / 4000)
        for (i in list.indices step step) {
            val s = String(blob, idx[i], idx[i + 1] - idx[i], Charsets.UTF_8)
            assertTrue("${list[i]} 의 뜻이 비었거나 깨짐", s.isNotBlank())
            assertTrue("${list[i]} 의 뜻에 대체 문자가 있음", '�' !in s)
        }
    }
}
