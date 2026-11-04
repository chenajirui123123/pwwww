package com.wardrobe.atelier

import android.app.Activity
import android.app.Instrumentation
import android.content.*
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wardrobe.atelier.data.LocalStore
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportRotationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @After
    fun leaveWelcomeForOtherJourneys() {
        LocalStore(InstrumentationRegistry.getInstrumentation().targetContext).use { it.logout() }
    }

    private fun await(text: String) {
        ui.waitUntil(12000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun batchImportSurvivesRecreationWhileReadingPhoto() {
        await("直接开始我的衣橱")
        ui.onNodeWithText("直接开始我的衣橱").performClick()
        await("批量录入")
        ui.onNodeWithText("批量录入").performScrollTo().performClick()
        await("选择多张照片")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uri = Uri.parse("content://com.wardrobe.atelier.test.images/slow.png")
        val monitor =
            instrumentation.addMonitor(
                IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addDataType("*/*")
                },
                Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)),
                true,
            )
        try {
            ui.onNodeWithText("选择多张照片").performClick()
            ui.waitUntil(3000) {
                ViewModelProvider(ui.activity)[WardrobeViewModel::class.java].state.value.busy
            }
            ui.activityRule.scenario.recreate()
            org.junit.Assert.assertTrue(
                "Photo must still be loading after recreation",
                ViewModelProvider(ui.activity)[WardrobeViewModel::class.java].state.value.busy,
            )
            await("保存这 1 件衣物")
            ui.onNodeWithText("保存这 1 件衣物").performClick()
            await("想找哪一件？")
            LocalStore(instrumentation.targetContext).use { store ->
                val data = store.snapshot()
                org.junit.Assert.assertTrue(data.garments.any { it.image.isNotBlank() })
                org.junit.Assert.assertTrue(data.wears.isEmpty())
                org.junit.Assert.assertTrue(data.careLogs.isEmpty())
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
