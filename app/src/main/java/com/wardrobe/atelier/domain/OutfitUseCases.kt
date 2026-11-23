package com.wardrobe.atelier.domain

/** Applies single-piece replacement constraints before calling the outfit rules. */
class GenerateOutfitUseCase {
    operator fun invoke(
        wardrobe: List<Garment>,
        request: InspirationRequest,
        locked: Set<Long>,
        excluded: Set<Long>,
        seen: Set<String>,
        currentPieces: List<Garment> = emptyList(),
        replace: Long? = null,
    ): Inspiration {
        if (replace == null) return InspirationEngine.generate(wardrobe, request, locked, excluded, seen)

        val target = requireNotNull(currentPieces.find { it.id == replace })
        val remaining = currentPieces.filter { it.id != replace }.map { it.id }.toSet()
        val sameShape = request.copy(
            structure = if (currentPieces.any { it.category == "连衣裙" }) "连衣裙" else "上下装",
            outerwear = currentPieces.any { it.category == "外套" },
            bag = currentPieces.any { it.category == "包袋" },
        )
        val outfit = InspirationEngine.generate(
            wardrobe, sameShape, remaining, excluded + replace, seen, requiredCategory = target.category,
        )
        require(outfit.items.any { it.category == target.category }) { "这一件暂时没有合适的替换。其他单品保留原样。" }
        return outfit
    }
}

class GenerateCapsuleUseCase {
    operator fun invoke(wardrobe: List<Garment>, count: Int, limit: Int, season: String, outerwear: Boolean): CapsuleResult =
        CapsuleEngine.generate(wardrobe, count, limit, season, outerwear)
}
