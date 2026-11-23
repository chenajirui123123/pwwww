package com.wardrobe.atelier.domain

/** Device-wide display preference, independent of wardrobe accounts and storage. */
interface LanguageSettings {
    fun isEnglish(): Boolean
    fun saveEnglish(english: Boolean)
    fun translations(): Map<String, String>
}
