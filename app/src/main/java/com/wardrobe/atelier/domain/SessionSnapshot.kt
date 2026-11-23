package com.wardrobe.atelier.domain

/** An account and its data travel together, so a view never mixes two owners. */
data class SessionSnapshot(val account: Account?, val data: Wardrobe)
