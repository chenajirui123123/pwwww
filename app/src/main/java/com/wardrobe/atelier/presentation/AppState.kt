package com.wardrobe.atelier.presentation

import com.wardrobe.atelier.domain.*

data class AppState(
    val loaded: Boolean = false,
    val account: Account? = null,
    val data: Wardrobe = Wardrobe(),
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val needsReload: Boolean = false,
)

