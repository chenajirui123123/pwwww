package com.wardrobe.atelier.data

import android.content.Context
import com.wardrobe.atelier.domain.LanguageSettings
import org.json.JSONObject

class LocalLanguageSettings(context: Context) : LanguageSettings {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    override fun isEnglish(): Boolean = preferences.getBoolean("english", false)
    override fun saveEnglish(english: Boolean) {
        preferences.edit().putBoolean("english", english).apply()
    }
    override fun translations(): Map<String, String> {
        val json = JSONObject(context.assets.open("english.json").bufferedReader().use { it.readText() })
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
}
