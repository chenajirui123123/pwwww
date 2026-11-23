package com.wardrobe.atelier

import com.wardrobe.atelier.domain.*
import org.junit.Assert.*
import org.junit.Test

class OutfitUseCasesTest {
    @Test fun swappingOnePiecePreservesOthersAndExcludesOriginal() {
        val top = Garment(id = 1, name = "Top", category = "上装")
        val old = Garment(id = 2, name = "Old", category = "下装")
        val replacement = Garment(id = 3, name = "New", category = "下装")
        val wardrobe = listOf(top, old, replacement)
        val result = GenerateOutfitUseCase()(wardrobe, InspirationRequest(), emptySet(), emptySet(), emptySet(), listOf(top, old), old.id)
        assertEquals(setOf(1L, 3L), result.items.map { it.id }.toSet())
        assertEquals(listOf(top, old, replacement), wardrobe)
    }

    @Test(expected = IllegalArgumentException::class)
    fun capsuleRejectsInvalidPackingRequest() {
        GenerateCapsuleUseCase()(emptyList(), 0, 6, "不限", false)
    }
}
