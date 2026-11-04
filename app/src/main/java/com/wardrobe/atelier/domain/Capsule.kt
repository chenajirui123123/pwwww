package com.wardrobe.atelier.domain

import kotlin.random.Random

/** A saved packing idea, not a planned or actual wearing record. */
data class Capsule(
    val id: Long = 0,
    val name: String = "",
    val season: String = "不限",
    val looks: List<List<Long>> = emptyList(),
    val packed: List<Long> = emptyList(),
    val notes: String = "",
) {
    val items: List<Long>
        get() = looks.flatten().distinct()
}

data class CapsuleResult(val looks: List<List<Garment>>, val requested: Int, val limit: Int) {
    val items
        get() = looks.flatten().distinctBy { it.id }

    val message
        get() =
            if (looks.size < requested) "本次找到 ${looks.size} 套不同穿搭；可以增加携带件数，或放宽季节。"
            else "用 ${items.size} 件衣物组成 ${looks.size} 套穿搭。"
}

/** Greedy reuse over sampled valid looks; deliberately makes no global-optimality claim. */
object CapsuleEngine {
    fun generate(
        clothes: List<Garment>,
        count: Int = 3,
        limit: Int = 6,
        season: String = "不限",
        outerwear: Boolean = false,
        random: Random = Random.Default,
    ): CapsuleResult {
        require(count in 1..7 && limit in 2..12) { "请检查组合数与携带件数" }
        if (outerwear)
            require(
                clothes.any {
                    !it.archived &&
                        it.category == "外套" &&
                        (season == "不限" || it.season in setOf(season, "四季", "未标注"))
                }
            ) {
                "还没有符合季节的外套，可以取消外套或放宽季节。"
            }
        val request = InspirationRequest(season = season, outerwear = outerwear)
        val candidates = linkedMapOf<String, List<Garment>>()
        repeat(64) {
            val look =
                InspirationEngine.generate(
                    clothes,
                    request,
                    seen = candidates.keys,
                    random = random,
                )
            if (look.items.size <= limit) candidates[look.signature] = look.items
        }
        require(candidates.isNotEmpty()) { "当前携带件数不足以组成一套，请增加件数或取消外套。" }
        fun bodyKey(look: List<Garment>) =
            look.filter { it.category in setOf("上装", "下装", "连衣裙") }.map { it.id }.sorted()
        val attempts =
            candidates.values.shuffled(random).take(16).map { first ->
                val selected = mutableListOf(first)
                val used = first.map { it.id }.toMutableSet()
                val bodies = mutableSetOf(bodyKey(first))
                while (selected.size < count) {
                    val next =
                        candidates.values
                            .shuffled(random)
                            .filter {
                                bodyKey(it) !in bodies &&
                                    (used + it.map { g -> g.id }).size <= limit
                            }
                            .minByOrNull { look -> look.count { it.id !in used } } ?: break
                    selected += next
                    used += next.map { it.id }
                    bodies += bodyKey(next)
                }
                selected
            }
        val best =
            attempts
                .sortedWith(
                    compareByDescending<List<List<Garment>>> { it.size }
                        .thenBy { looks -> looks.flatten().distinctBy { it.id }.size }
                )
                .first()
        return CapsuleResult(best, count, limit)
    }
}
