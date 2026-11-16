package com.wardrobe.atelier

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wardrobe.atelier.ui.LanguagePreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageTest {
    private val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain.outerRule(object : org.junit.rules.ExternalResource() {
        override fun before() {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            check(context.packageName.endsWith(".preview"))
            com.wardrobe.atelier.data.LocalStore(context).use { it.logout() }
        }
    }).around(ui)
    private fun shot(name: String) {
        ui.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val folder = java.io.File(ui.activity.getExternalFilesDir(null), "language-qa").apply { mkdirs() }
        java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun switchLanguageAndRestoreAfterRecreation() {
        ui.waitUntil(10000) { ui.onAllNodesWithText("English").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("English").performScrollTo().performClick()
        ui.onNodeWithText("Start my wardrobe").assertExists()
        ui.onNodeWithText("Explore the demo wardrobe").assertExists()
        shot("01-english-welcome")
        assertTrue(ui.activity.getSharedPreferences("appearance", 0).getBoolean("english", false))
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithText("Start my wardrobe").assertExists()
        ui.onNodeWithText("Explore the demo wardrobe").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("What are you looking for?").fetchSemanticsNodes().isNotEmpty() }
        shot("02-english-wardrobe")
        ui.runOnIdle {
            com.wardrobe.atelier.data.LocalStore(ui.activity).use { store ->
                val g = store.snapshot().active.first()
                assertTrue(com.wardrobe.atelier.ui.matchesLocalized(g, "${com.wardrobe.atelier.ui.localized(g.color)} ${com.wardrobe.atelier.ui.localized(g.category)}"))
                assertTrue(com.wardrobe.atelier.ui.matchesLocalized(g, g.name))
            }
        }
        val before = com.wardrobe.atelier.data.LocalStore(ui.activity).use { it.snapshot() }
        ui.onNodeWithText("Inspiration", substring = false).performClick()
        ui.waitForIdle()
        shot("03-english-inspiration")
        ui.onNodeWithText("My space", substring = false).performClick()
        ui.onNodeWithText("English").assertExists()
        shot("04-english-profile")
        ui.onNodeWithText("Travel light", substring = false).performScrollTo().performClick()
        ui.waitForIdle()
        shot("05-english-travel")
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("简体中文").performScrollTo().performClick()
        val after = com.wardrobe.atelier.data.LocalStore(ui.activity).use { it.snapshot() }
        assertEquals(before, after)
        ui.onNodeWithText("我的", substring = false).assertExists()
        assertFalse(ui.activity.getSharedPreferences("appearance", 0).getBoolean("english", true))
        ui.runOnIdle { assertFalse(LanguagePreferences.english) }
    }
}
