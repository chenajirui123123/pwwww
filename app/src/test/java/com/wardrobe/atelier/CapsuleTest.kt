package com.wardrobe.atelier

import com.wardrobe.atelier.domain.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class CapsuleTest {
    private val clothes =
        listOf(
            Garment(1, "衬衫", "上装"),
            Garment(2, "T恤", "上装"),
            Garment(3, "针织", "上装"),
            Garment(4, "长裤", "下装"),
            Garment(5, "短裤", "下装"),
            Garment(6, "运动鞋", "鞋履"),
            Garment(7, "外套", "外套"),
            Garment(8, "连衣裙", "连衣裙"),
        )

    @Test
    fun reusesPiecesWithinLimitAndKeepsEveryLookComplete() {
        repeat(8) {
            val result = CapsuleEngine.generate(clothes, count = 5, limit = 6, random = Random(it))
            assertTrue(result.looks.size in 1..5)
            assertTrue(result.items.size <= 6)
            result.looks.forEach { look ->
                val categories = look.map { g -> g.category }
                assertTrue("连衣裙" in categories || "上装" in categories && "下装" in categories)
                assertTrue("鞋履" in categories)
            }
            assertEquals(
                result.looks.size,
                result.looks
                    .map { look ->
                        look
                            .filter { g -> g.category in setOf("上装", "下装", "连衣裙") }
                            .map { g -> g.id }
                            .sorted()
                    }
                    .distinct()
                    .size,
            )
        }
    }

    @Test
    fun oneBodyCombinationCannotBeInflatedByChangingShoes() {
        val source = clothes.filter { it.id in setOf(1L, 4L, 6L) } + Garment(9, "另一双鞋", "鞋履")
        assertEquals(1, CapsuleEngine.generate(source, count = 3).looks.size)
    }

    @Test
    fun seasonArchiveAndOuterwearRequirementsCarryThrough() {
        val source =
            clothes.map {
                if (it.id == 1L) it.copy(archived = true)
                else if (it.id == 2L) it.copy(season = "冬季") else it
            }
        val result = CapsuleEngine.generate(source, season = "夏季", outerwear = true)
        assertFalse(result.items.any { it.id in setOf(1L, 2L) })
        assertTrue(result.looks.all { row -> row.any { it.category == "外套" } })
    }

    @Test
    fun insufficientClosetAndInvalidLimitsFailClearly() {
        assertThrows(IllegalArgumentException::class.java) { CapsuleEngine.generate(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            CapsuleEngine.generate(clothes, limit = 1)
        }
    }
}
