package com.wardrobe.atelier.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

val Categories = listOf("上装", "下装", "连衣裙", "外套", "鞋履", "包袋", "配饰")
val Silhouettes =
    mapOf(
        "上装" to listOf("衬衫", "T恤", "针织", "卫衣"),
        "下装" to listOf("长裤", "短裤", "半裙"),
        "连衣裙" to listOf("连衣裙"),
        "外套" to listOf("夹克", "风衣", "西装"),
        "鞋履" to listOf("运动鞋", "低跟鞋", "短靴"),
        "包袋" to listOf("手提包", "双肩包"),
        "配饰" to listOf("围巾", "帽子"),
    )
val Seasons = listOf("未标注", "四季", "春秋", "夏季", "冬季")
val PaletteNames = listOf("米白", "黑色", "灰色", "蓝色", "绿色", "棕色", "粉色", "其他")
val PaletteHex =
    listOf(
        0xFFE8DFCC,
        0xFF393B3A,
        0xFF9B9F9D,
        0xFF7E9BB7,
        0xFF87957A,
        0xFF9D775E,
        0xFFD4A5A1,
        0xFFD8BE86,
    )

enum class Care(val label: String) {
    READY("可穿"),
    DIRTY("待洗"),
    WASHING("清洗中"),
}

data class Account(val id: Long, val username: String, val nickname: String)

data class Garment(
    val id: Long = 0,
    val name: String = "",
    val category: String = "上装",
    val color: String = "米白",
    val season: String = "四季",
    val location: String = "衣柜",
    val brand: String = "",
    val price: Double = 0.0,
    val notes: String = "",
    val image: String = "",
    val care: Care = Care.READY,
    val archived: Boolean = false,
    val created: String = LocalDate.now().toString(),
    val style: String = "",
)

data class Outfit(
    val id: Long = 0,
    val name: String = "",
    val occasion: String = "日常",
    val notes: String = "",
    val items: List<Long> = emptyList(),
)

data class Plan(
    val id: Long = 0,
    val date: String = LocalDate.now().toString(),
    val title: String = "",
    val items: List<Long> = emptyList(),
    val outfitId: Long = 0,
    val notes: String = "",
)

data class Wear(
    val id: Long = 0,
    val date: String = LocalDate.now().toString(),
    val items: List<Long> = emptyList(),
    val notes: String = "",
    val planId: Long? = null,
    val voided: Boolean = false,
)

data class CareLog(
    val id: Long,
    val garmentId: Long,
    val before: Care,
    val after: Care,
    val time: String,
)

data class Wardrobe(
    val garments: List<Garment> = emptyList(),
    val outfits: List<Outfit> = emptyList(),
    val plans: List<Plan> = emptyList(),
    val wears: List<Wear> = emptyList(),
    val careLogs: List<CareLog> = emptyList(),
    val locations: List<String> = emptyList(),
    val capsules: List<Capsule> = emptyList(),
) {
    val active
        get() = garments.filterNot { it.archived }

    fun pieces(ids: List<Long>) = ids.mapNotNull { id -> garments.find { it.id == id } }

    fun wearCount(id: Long) = wears.count { !it.voided && id in it.items }

    fun idleDays(g: Garment, today: LocalDate = LocalDate.now()): Long {
        val last =
            wears.filter { !it.voided && g.id in it.items }.maxOfOrNull { it.date } ?: g.created
        return ChronoUnit.DAYS.between(LocalDate.parse(last), today).coerceAtLeast(0)
    }
}

object Rules {
    fun wear(date: String, ids: List<Long>, today: LocalDate = LocalDate.now()) {
        require(ids.isNotEmpty()) { "请至少选择一件衣物" }
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
        require(parsed != null) { "请选择有效的日期" }
        require(!parsed!!.isAfter(today)) { "实际穿着日期不能晚于今天" }
        require(ids.distinct().size == ids.size) { "同一件衣物不能重复选择" }
    }

    fun garment(g: Garment) {
        require(g.name.trim().length in 1..40) { "衣物名称应为 1–40 个字" }
        require(g.category in Categories && g.season in Seasons && g.color in PaletteNames) {
            "请选择有效的衣物属性"
        }
        require(g.style.isBlank() || g.style in Silhouettes.getValue(g.category)) { "款式与衣物类别不匹配" }
        require(g.price.isFinite() && g.price in 0.0..999999.0) { "价格应在 0–999999 元之间" }
        require(
            g.notes.length <= 500 && g.brand.length <= 40 && g.location.trim().length in 1..40
        ) {
            "请检查品牌、备注或收纳位置的长度"
        }
    }
}
