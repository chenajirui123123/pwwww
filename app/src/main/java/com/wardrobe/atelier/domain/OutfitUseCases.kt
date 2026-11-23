package com.wardrobe.atelier.domain

/** Pure application use cases: no Android, Compose, SQLite or presentation dependencies. */
class GenerateOutfitUseCase {
    operator fun invoke(wardrobe: List<Garment>, request: InspirationRequest, locked: Set<Long>, excluded: Set<Long>, seen: Set<String>, currentPieces: List<Garment> = emptyList(), replace: Long? = null): Inspiration {
        val pins = if (replace != null) currentPieces.map { it.id }.toSet() - replace else locked
        val skips = if (replace != null) excluded + replace else excluded
        val effectiveRequest = if (replace != null) request.copy(
            structure = if (currentPieces.any { it.category == "连衣裙" }) "连衣裙" else "上下装"
        ) else request
        return InspirationEngine.generate(wardrobe, effectiveRequest, pins, skips, seen)
    }
}

class GenerateCapsuleUseCase {
    operator fun invoke(wardrobe: List<Garment>, count: Int, limit: Int, season: String, outerwear: Boolean): CapsuleResult =
        CapsuleEngine.generate(wardrobe, count, limit, season, outerwear)
}
