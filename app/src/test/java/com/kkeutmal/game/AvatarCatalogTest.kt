package com.kkeutmal.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarCatalogTest {

    @Test
    fun `아바타는 형태 4 곱하기 색 6 곱하기 표정 2 로 48종`() {
        assertEquals(48, AvatarCatalog.ALL.size)
        assertEquals(48, AvatarCatalog.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `등급별 개수가 스펙과 일치한다`() {
        val byGrade = AvatarCatalog.ALL.groupingBy { it.grade }.eachCount()
        assertEquals(24, byGrade[AvatarGrade.COMMON])
        assertEquals(12, byGrade[AvatarGrade.RARE])
        assertEquals(8, byGrade[AvatarGrade.EPIC])
        assertEquals(4, byGrade[AvatarGrade.LEGENDARY])
    }

    @Test
    fun `기본 표정은 전부 일반 등급이고 코인으로 산다`() {
        val basics = AvatarCatalog.ALL.filter { it.face == AvatarFace.BASIC }
        assertEquals(24, basics.size)
        assertTrue(basics.all { it.grade == AvatarGrade.COMMON })
        assertTrue(basics.all { it.unlock is Unlock.Coin })
    }

    @Test
    fun `일반 등급 가격은 몸 형태로 정해진다`() {
        val square = AvatarCatalog.ALL.first { it.shape == AvatarShape.SQUARE && it.face == AvatarFace.BASIC }
        val rhombus = AvatarCatalog.ALL.first { it.shape == AvatarShape.RHOMBUS && it.face == AvatarFace.BASIC }
        assertEquals(150, (square.unlock as Unlock.Coin).price)
        assertEquals(300, (rhombus.unlock as Unlock.Coin).price)
    }

    @Test
    fun `아이디와 이름은 규칙적으로 생성된다`() {
        val def = AvatarCatalog.byId("square_blue_basic")
        assertNotNull(def)
        assertEquals("방긋 파랑 네모", def!!.name)
        assertEquals("blue_body_square", def.bodyAsset)
    }

    @Test
    fun `기본 아바타는 카탈로그에 있고 가장 싸다`() {
        val def = AvatarCatalog.byId(AvatarCatalog.DEFAULT_ID)
        assertNotNull(def)
        assertEquals(AvatarGrade.COMMON, def!!.grade)
    }

    @Test
    fun `희귀는 레벨 12개 영웅은 보스 8개 전설은 도전과제 4개로 해금된다`() {
        val rare = AvatarCatalog.ALL.filter { it.grade == AvatarGrade.RARE }
        assertEquals(
            listOf(5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70, 80),
            rare.map { (it.unlock as Unlock.Level).level }.sorted()
        )
        val epic = AvatarCatalog.ALL.filter { it.grade == AvatarGrade.EPIC }
        assertEquals(
            listOf(5, 10, 15, 20, 25, 30, 35, 40),
            epic.map { (it.unlock as Unlock.BossClear).stage }.sorted()
        )
        val legendary = AvatarCatalog.ALL.filter { it.grade == AvatarGrade.LEGENDARY }
        assertEquals(4, legendary.size)
        assertTrue(legendary.all { it.unlock is Unlock.Achieve })
    }

    @Test
    fun `해금 안내 문구가 사람이 읽을 수 있게 나온다`() {
        val rare = AvatarCatalog.ALL.first { it.grade == AvatarGrade.RARE && (it.unlock as Unlock.Level).level == 5 }
        assertEquals("레벨 5 달성", AvatarCatalog.unlockDescription(rare))
        val epic = AvatarCatalog.ALL.first { it.grade == AvatarGrade.EPIC && (it.unlock as Unlock.BossClear).stage == 15 }
        assertEquals("15스테이지 보스 클리어", AvatarCatalog.unlockDescription(epic))
    }
}
