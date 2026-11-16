package com.wardrobe.atelier.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** UI language only: persisted domain keys and user-entered content are never rewritten. */
object LanguagePreferences {
    var english by mutableStateOf(false)
        private set
    private var initialized = false
    private var translations = emptyMap<String, String>()

    fun initialize(context: Context) {
        if (initialized) return
        val json = JSONObject(context.assets.open("english.json").bufferedReader().use { it.readText() })
        translations = json.keys().asSequence().associateWith { json.getString(it) }
        english = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("english", false)
        initialized = true
    }

    fun select(context: Context, useEnglish: Boolean) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putBoolean("english", useEnglish).apply()
        english = useEnglish
    }

    fun text(value: String): String {
        if (!english) return value
        translations[value]?.let { return it }
        EnglishTemplates.translate(value)?.let { return it }
        // Composite labels are UI-owned; unknown parts (including user content) stay verbatim.
        for (separator in listOf(" · ", " / ")) {
            if (separator in value) return value.split(separator).joinToString(separator) { translations[it] ?: EnglishTemplates.translate(it) ?: it }
        }
        return value
    }
}

fun localized(value: String): String = LanguagePreferences.text(value)

/** English tags supplement, rather than replace, literal name/location search. */
fun matchesLocalized(g: com.wardrobe.atelier.domain.Garment, query: String): Boolean {
    if (com.wardrobe.atelier.domain.WardrobeSearch.matches(g, query)) return true
    val searchable = listOf(g.name, g.brand, g.location, g.notes, localized(g.category), localized(g.style), localized(g.color), localized(g.season)).joinToString(" ").lowercase()
    return query.trim().lowercase().split(Regex("[\\s,/]+"))
        .filter { it.isNotBlank() }.all { searchable.contains(it) }
}

@Composable
fun LanguageSelector() {
    val context = LocalContext.current
    LanguagePreferences.initialize(context)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (LanguagePreferences.english) "Language / 语言" else "语言 / Language")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Teal), selected = !LanguagePreferences.english,
                onClick = { LanguagePreferences.select(context, false) }, label = { Text("简体中文") })
            FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Teal), selected = LanguagePreferences.english,
                onClick = { LanguagePreferences.select(context, true) }, label = { Text("English") })
        }
    }
}

/** Anchored templates keep dynamic values separate from translated interface copy. */
internal object EnglishTemplates {
    private val rules = listOf(
        "衣橱里已有 (\\d+) 件同类别衣物" to "$1 items in this category already owned",
        "衣橱里已有 (\\d+) 件同色同类别衣物" to "$1 items in this category and color already owned",
        "(\\d+) 件单品" to "$1 pieces",
        "重命名(.*)" to "Rename $1",
        "删除(.*)" to "Delete $1",
        "按(.*)筛选" to "Season: $1",
        "偏向(.*)款式" to "Style: $1",
        "没有超过 (.*) 未更新穿着记录的衣物。" to "No items without a wear record for over $1.",
        "(.*)有穿着记录的衣物" to "Items with wear records: $1",
        "(.*)列表为空" to "No items: $1",
        "存入：(.*)。图片会放入新衣物草稿，补上类别和位置后再保存。当前未完成的页面会保留。" to "Save to: $1. Add a category and location to the new image draft. Your current page will be kept.",
        "已选 (\\d+) 件" to "$1 selected",
        "已拥有 (\\d+) 件" to "$1 items owned",
        "(\\d+) 件衣物，记在这里就好。" to "$1 items, remembered here.",
        "继续加照片（(\\d+)/20）" to "Add photos ($1/20)",
        "第 (\\d+) 件" to "Item $1",
        "第 (\\d+) 件名字（可不填）" to "Item $1 name (optional)",
        "保存这 (\\d+) 件衣物" to "Save $1 items",
        "已记下 (\\d+) 件衣物" to "$1 items saved",
        "\\+(\\d+) 件 · 见单品列表" to "+$1 items · See list",
        "锁定 (\\d+) 件 · 本轮跳过 (\\d+) 件" to "$1 locked · $2 excluded",
        "(\\d+) 件 · (\\d+) 套 · 已装 (\\d+)/(\\d+)" to "$1 items · $2 outfits · $3/$4 packed",
        "(\\d+) 套" to "$1 outfits",
        "(\\d+) 件" to "$1 items",
        "组合 (\\d+)" to "Outfit $1",
        "(\\d+)次" to "$1 times",
        "(\\d+)天" to "$1 days",
        "(\\d+) 天无记录" to "$1 days without records",
        "(\\d+) 件衣物（含归档）" to "$1 items (including archived)",
        "(\\d+) 月" to "Month $1",
        "(\\d+) 月 (\\d+) 日" to "$1/$2",
        "(\\d+) 件单品 · 计划" to "$1 pieces · Planned",
        "(\\d+) 件有待处理的手动标记" to "$1 manually flagged items",
        "本次找到 (\\d+) 套不同穿搭；可以增加携带件数，或放宽季节。" to "$1 different outfits found. Increase the packing limit or broaden the season.",
        "用 (\\d+) 件衣物组成 (\\d+) 套穿搭。" to "$2 outfits using $1 pieces."
    ).map { (pattern, replacement) -> Regex("^$pattern$") to replacement }
    fun translate(value: String): String? {
        for ((pattern, replacement) in rules) {
            if (pattern.matches(value)) return pattern.replace(value, replacement)
        }
        return null
    }
}
