package com.wardrobe.atelier.ui

import com.wardrobe.atelier.presentation.LanguagePreferences

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (LanguagePreferences.english) "Language / 语言" else "语言 / Language")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Teal), selected = !LanguagePreferences.english,
                onClick = { LanguagePreferences.select( false) }, label = { Text("简体中文") })
            FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Teal), selected = LanguagePreferences.english,
                onClick = { LanguagePreferences.select( true) }, label = { Text("English") })
        }
    }
}
