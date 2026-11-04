package com.wardrobe.atelier

import com.wardrobe.atelier.domain.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class DiscoveryTest {
    private val clothes =
        listOf(
            Garment(1, "白衬衫", "上装", "米白", style = "衬衫", location = "宿舍衣柜 · 上层"),
            Garment(2, "蓝色T恤", "上装", "蓝色", style = "T恤"),
            Garment(3, "西裤", "下装", "黑色", style = "长裤"),
            Garment(4, "裙子", "下装", "灰色", style = "半裙"),
            Garment(5, "连衣裙", "连衣裙", "蓝色", style = "连衣裙"),
            Garment(6, "球鞋", "鞋履", "米白", style = "运动鞋", location = "宿舍鞋架"),
            Garment(7, "蓝色夹克", "外套", "蓝色", style = "夹克"),
            Garment(8, "包", "包袋", "棕色", style = "手提包"),
        )

    @Test
    fun combinesAttributeKeywordsAndLocation() {
        assertTrue(WardrobeSearch.matches(clothes[6], "蓝色外套"))
        assertFalse(WardrobeSearch.matches(clothes[1], "蓝色外套"))
        assertTrue(WardrobeSearch.matches(clothes[5], "宿舍 鞋"))
        assertTrue(WardrobeSearch.matches(clothes[0], "白色 上衣 上层"))
        assertFalse(WardrobeSearch.matches(clothes[0], "白色 上衣 鞋架"))
    }

    @Test
    fun bodiesStayStructurallyCompatibleAcrossSeeds() {
        repeat(40) { seed ->
            val result =
                InspirationEngine.generate(
                    clothes,
                    InspirationRequest(outerwear = true, bag = true),
                    random = Random(seed),
                )
            val categories = result.items.map { it.category }
            assertEquals(categories.size, categories.toSet().size)
            assertTrue(
                if ("连衣裙" in categories) "上装" !in categories && "下装" !in categories
                else "上装" in categories && "下装" in categories
            )
            assertTrue("鞋履" in categories && "外套" in categories && "包袋" in categories)
        }
    }

    @Test
    fun lockedItemStaysAndArchivedAndExcludedItemsAreNeverChosen() {
        val source = clothes + Garment(9, "旧衣", "上装", archived = true)
        repeat(12) {
            val r =
                InspirationEngine.generate(
                    source,
                    locked = setOf(1),
                    excluded = setOf(4),
                    random = Random(it),
                )
            assertTrue(r.items.any { g -> g.id == 1L })
            assertFalse(r.items.any { g -> g.id in setOf(4L, 9L) })
        }
    }

    @Test
    fun newSuggestionsAvoidSeenCombinationsWhenAlternativesExist() {
        val first = InspirationEngine.generate(clothes, random = Random(1))
        val next =
            InspirationEngine.generate(clothes, seen = setOf(first.signature), random = Random(1))
        assertNotEquals(first.signature, next.signature)
        assertFalse(next.repeated)
    }

    @Test
    fun oneAvailableLookReportsRepetitionHonestly() {
        val source = clothes.filter { it.id in setOf(1L, 3L, 6L) }
        val first = InspirationEngine.generate(source)
        val second = InspirationEngine.generate(source, seen = setOf(first.signature))
        assertTrue(second.repeated)
        assertEquals(first.signature, second.signature)
    }

    @Test
    fun seasonalFilteringKeepsUnknownExplicitAndRejectsConflictingLock() {
        val source =
            listOf(clothes[0].copy(season = "冬季"), clothes[1].copy(season = "未标注"), clothes[2])
        val request = InspirationRequest(season = "夏季")
        val r = InspirationEngine.generate(source, request)
        assertFalse(r.items.any { it.id == 1L })
        assertTrue(r.notes.any { it.contains("未标注") })
        assertThrows(IllegalArgumentException::class.java) {
            InspirationEngine.generate(source, request, locked = setOf(1))
        }
    }

    @Test
    fun conflictingBodyLocksAreRejectedInsteadOfSilentlyDropped() {
        assertThrows(IllegalArgumentException::class.java) {
            InspirationEngine.generate(clothes, locked = setOf(1, 5))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InspirationEngine.generate(clothes, locked = setOf(1, 2))
        }
    }

    @Test
    fun shoesAloneDoNotPretendToBeAnOutfit() {
        assertThrows(IllegalArgumentException::class.java) {
            InspirationEngine.generate(clothes.filter { it.category == "鞋履" })
        }
    }

    @Test
    fun colorComparisonOnlyUsesKnownAttributesAndExcludesArchived() {
        val source = clothes + clothes[1].copy(id = 20, archived = true)
        assertEquals(listOf(2L), WardrobeSearch.similar(source, "上装", "蓝色").map { it.id })
    }

    @Test
    fun legacyLaundryStateDoesNotSuppressLowMaintenanceSuggestions() {
        val source = clothes.map { it.copy(care = Care.WASHING) }
        assertTrue(InspirationEngine.generate(source).items.isNotEmpty())
    }

    @Test
    fun literalNamesAndLocationsWorkWithoutOptionalColorTags() {
        val g = Garment(1, "蓝色衬衫", color = "其他", location = "鞋架旁的收纳箱")
        assertTrue(WardrobeSearch.matches(g, "蓝色衬衫"))
        assertTrue(WardrobeSearch.matches(g, "蓝色 上衣"))
        assertTrue(WardrobeSearch.matches(g, "鞋架"))
        assertTrue(WardrobeSearch.matches(clothes[0], "米白色上衣"))
    }
}
