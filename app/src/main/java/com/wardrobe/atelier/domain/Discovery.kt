package com.wardrobe.atelier.domain

import kotlin.random.Random

/** Transparent keyword matching: attributes + remaining words, never a claim of AI recognition. */
object WardrobeSearch {
    fun matches(g: Garment, query: String): Boolean {
        var rest = query.trim().lowercase()
        if (rest.isBlank()) return true
        val searchable =
            "${g.name} ${g.category} ${g.style} ${g.color} ${g.brand} ${g.location} ${g.notes}"
                .lowercase()
        // A literal name or location must remain searchable even when optional tags are absent.
        if (searchable.contains(rest)) return true
        val colors =
            mapOf(
                "米白色" to "米白",
                "白色" to "米白",
                "米白" to "米白",
                "黑色" to "黑色",
                "灰色" to "灰色",
                "蓝色" to "蓝色",
                "绿色" to "绿色",
                "棕色" to "棕色",
                "粉色" to "粉色",
            )
        val tokens = mutableListOf<String>()
        colors.keys
            .sortedByDescending { it.length }
            .forEach { token ->
                if (rest.contains(token)) {
                    tokens += token
                    rest = rest.replace(token, " ")
                }
            }
        if (
            tokens.isNotEmpty() &&
                tokens.none { colors[it] == g.color || g.name.contains(it, ignoreCase = true) }
        )
            return false
        val categories =
            mapOf(
                "连衣裙" to setOf("连衣裙"),
                "外套" to setOf("外套"),
                "上衣" to setOf("上装"),
                "上装" to setOf("上装"),
                "下装" to setOf("下装"),
                "鞋子" to setOf("鞋履"),
                "鞋履" to setOf("鞋履"),
                "鞋" to setOf("鞋履"),
                "包袋" to setOf("包袋"),
                "配饰" to setOf("配饰"),
            )
        val category =
            categories.keys.sortedByDescending { it.length }.firstOrNull { rest.contains(it) }
        if (category != null) {
            if (g.category !in categories.getValue(category)) return false
            rest = rest.replace(category, " ")
        }
        return rest
            .split(Regex("[\\s，,、/]+"))
            .filter { it.isNotBlank() && it != "的" }
            .all { searchable.contains(it) }
    }

    fun similar(
        items: List<Garment>,
        category: String,
        color: String,
        excluding: Long = 0,
    ): List<Garment> =
        items.filter {
            !it.archived &&
                it.id != excluding &&
                it.category == category &&
                (color == "其他" || it.color == color)
        }
}

data class InspirationRequest(
    val season: String = "不限",
    val structure: String = "自动",
    val mood: String = "随心",
    val outerwear: Boolean = false,
    val bag: Boolean = false,
)

data class Inspiration(val items: List<Garment>, val notes: List<String>, val repeated: Boolean) {
    val signature
        get() = items.map { it.id }.sorted().joinToString(",")
}

/**
 * Bounded, on-device composition. Randomness explores viable combinations; it does not infer wear.
 */
object InspirationEngine {
    fun generate(
        wardrobe: List<Garment>,
        request: InspirationRequest = InspirationRequest(),
        locked: Set<Long> = emptySet(),
        excluded: Set<Long> = emptySet(),
        seen: Set<String> = emptySet(),
        random: Random = Random.Default,
        requiredCategory: String? = null,
    ): Inspiration {
        val available = wardrobe.filter { !it.archived && it.id !in excluded }
        require(locked.all { id -> available.any { it.id == id } }) { "锁定的衣物已归档或被排除，请先清除锁定" }
        val pins = available.filter { it.id in locked }
        require(pins.groupBy { it.category }.all { it.value.size == 1 }) { "同一类别只能锁定一件衣物" }
        val pool =
            available.filter {
                request.season == "不限" || it.season in setOf(request.season, "四季", "未标注")
            }
        require(pins.all { p -> pool.any { it.id == p.id } }) { "锁定衣物不符合当前季节，请调整条件或解锁" }
        val hasDress = pins.any { it.category == "连衣裙" }
        val hasSeparates = pins.any { it.category in setOf("上装", "下装") }
        require(!(hasDress && hasSeparates)) { "连衣裙与上下装属于不同组合形式，请先解锁其中一组" }
        require(
            !(request.structure == "上下装" && hasDress) &&
                !(request.structure == "连衣裙" && hasSeparates)
        ) {
            "当前锁定与组合形式冲突，请先解锁或选择自动"
        }
        val grouped = pool.groupBy { it.category }
        val forms = buildList {
            if (
                request.structure != "连衣裙" &&
                    !hasDress &&
                    grouped["上装"].orEmpty().isNotEmpty() &&
                    grouped["下装"].orEmpty().isNotEmpty()
            )
                add(listOf("上装", "下装"))
            if (
                request.structure != "上下装" && !hasSeparates && grouped["连衣裙"].orEmpty().isNotEmpty()
            )
                add(listOf("连衣裙"))
        }
        require(forms.isNotEmpty()) { "还凑不齐这一类搭配。需要一件上衣和一件下装，或一件连衣裙；也可以放宽季节条件。" }
        val pinnedByCategory = pins.associateBy { it.category }
        val candidates = linkedMapOf<String, List<Garment>>()
        repeat(256) {
            val body = forms.random(random)
            val categories =
                (body +
                        listOf("鞋履") +
                        (if (request.outerwear) listOf("外套") else emptyList()) +
                        (if (request.bag) listOf("包袋") else emptyList()) +
                        listOfNotNull(requiredCategory) +
                        pins.map { it.category })
                    .distinct()
            val pieces =
                categories.mapNotNull { cat ->
                    pinnedByCategory[cat] ?: grouped[cat].orEmpty().randomOrNull(random)
                }
            val signature = pieces.map { it.id }.sorted().joinToString(",")
            candidates[signature] = pieces
        }
        val fresh = candidates.filterKeys { it !in seen }
        val source = if (fresh.isNotEmpty()) fresh else candidates
        val ranked = source.values.sortedByDescending { score(it, request.mood) }
        val chosen = ranked.take(8).random(random)
        val notes = buildList {
            add(if (chosen.any { it.category == "连衣裙" }) "连衣裙组合" else "上装＋下装组合")
            if (request.season != "不限") add("按${request.season}筛选")
            if (request.mood != "随心") add("偏向${request.mood}款式")
            if (chosen.any { it.season == "未标注" } && request.season != "不限") add("包含未标注季节的衣物，请留意厚薄")
            if (chosen.none { it.category == "鞋履" }) add("衣橱中还没有符合条件的鞋，可自行补充")
            if (request.outerwear && chosen.none { it.category == "外套" }) add("没有符合条件的外套")
            if (request.bag && chosen.none { it.category == "包袋" }) add("没有符合条件的包袋")
        }
        return Inspiration(chosen, notes, fresh.isEmpty())
    }

    private fun score(items: List<Garment>, mood: String): Int {
        val accentColors =
            items.map { it.color }.filter { it !in setOf("米白", "黑色", "灰色", "其他") }.distinct().size
        val preferred =
            when (mood) {
                "通勤" -> setOf("衬衫", "西装", "长裤", "风衣", "低跟鞋", "手提包")
                "休闲" -> setOf("T恤", "针织", "卫衣", "运动鞋", "短裤", "双肩包")
                "运动" -> setOf("T恤", "卫衣", "运动鞋", "短裤", "双肩包")
                else -> emptySet()
            }
        return items.count { it.style in preferred } * 3 - (accentColors - 2).coerceAtLeast(0) * 2
    }
}
