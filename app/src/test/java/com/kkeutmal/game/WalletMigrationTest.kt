package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletMigrationTest {

    @Test
    fun `아무것도 없으면 기본 아바타만 준다`() {
        val ids = Wallet.migratedAvatarIds(emptySet())
        assertEquals(setOf(AvatarCatalog.DEFAULT_ID), ids)
    }

    @Test
    fun `기존에 산 이모지 개수만큼 일반 아바타를 준다`() {
        val old = setOf("🙂", "😎", "🐱", "🐶")
        val ids = Wallet.migratedAvatarIds(old)
        // 기본 아바타 + 이모지 4개분 = 최소 4종 (기본이 이미 포함될 수 있어 4 이상)
        assertTrue(ids.size >= 4)
        assertTrue(AvatarCatalog.DEFAULT_ID in ids)
    }

    @Test
    fun `변환 결과는 전부 실재하는 일반 등급 아바타다`() {
        val ids = Wallet.migratedAvatarIds(setOf("🙂", "😎", "🐱", "🐶", "🦊", "🐼"))
        for (id in ids) {
            val def = AvatarCatalog.byId(id)
            assertTrue("존재하지 않는 아바타: $id", def != null)
            assertEquals(AvatarGrade.COMMON, def!!.grade)
        }
    }

    @Test
    fun `이모지가 아무리 많아도 일반 등급 24종을 넘지 않는다`() {
        val many = (1..100).map { "e$it" }.toSet()
        val ids = Wallet.migratedAvatarIds(many)
        assertEquals(24, ids.size)
    }

    @Test
    fun `같은 입력이면 항상 같은 결과가 나온다`() {
        val old = setOf("🙂", "🦊", "👑")
        assertEquals(Wallet.migratedAvatarIds(old), Wallet.migratedAvatarIds(old))
    }

    @Test
    fun `새 아이템 두 종이 추가돼 있다`() {
        val ids = Wallet.ITEMS.map { it.id }
        assertTrue("item_revive" in ids)
        assertTrue("item_double" in ids)
        assertEquals(200, Wallet.ITEMS.first { it.id == "item_revive" }.price)
        assertEquals(120, Wallet.ITEMS.first { it.id == "item_double" }.price)
    }
}
