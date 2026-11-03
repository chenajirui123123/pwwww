package com.wardrobe.atelier

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real UI, database, screen transitions and edits as a single journey. */
@RunWith(AndroidJUnit4::class)
class JourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private val garmentName = "测试雾蓝连衣裙" + (System.nanoTime() % 10000)
    private val outfitName = "测试日常搭配" + (System.nanoTime() % 10000)

    private fun hideKeyboard() {
        ui.runOnUiThread {
            WindowInsetsControllerCompat(ui.activity.window, ui.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        Thread.sleep(450) // IME animation runs outside the Compose test clock.
        ui.waitForIdle()
    }

    private fun awaitText(text: String) {
        try {
            ui.waitUntil(10000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            shot("failure")
            val roots = ui.onAllNodes(isRoot(), useUnmergedTree = true)
            val tree =
                roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString() }
            throw AssertionError("Waiting for $text\n$tree", e)
        }
        ui.waitForIdle()
    }

    private fun shot(name: String) {
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(ui.activity.getExternalFilesDir(null), "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun fullWardrobeJourney() {
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("体验示例衣橱").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodesWithText("我的衣橱").fetchSemanticsNodes().isNotEmpty()
        }
        if (ui.onAllNodesWithText("体验示例衣橱").fetchSemanticsNodes().isEmpty()) {
            ui.onNodeWithContentDescription("个人中心").performClick()
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("退出当前账号"))
            ui.onNodeWithText("退出当前账号").performScrollTo().performClick()
            ui.onNodeWithText("确认", substring = false).performClick()
        }
        awaitText("体验示例衣橱")
        shot("01-welcome")
        ui.onNodeWithText("体验示例衣橱").performScrollTo().performClick()
        awaitText("我的衣橱")
        shot("02-wardrobe")
        ui.onNodeWithText("录入衣物").performClick()
        awaitText("衣物名称 *")
        ui.onNodeWithText("衣物名称 *").performTextInput(garmentName)
        hideKeyboard()
        ui.onNodeWithText("连衣裙").performScrollTo().performClick()
        ui.onNodeWithText("保存").performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitText("我的衣橱")
        ui.onNodeWithText("搜索衣物、品牌或位置").performTextInput(garmentName)
        hideKeyboard()
        ui.onNode(hasText(garmentName) and !hasSetTextAction())
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        awaitText("衣物档案")
        ui.activityRule.scenario.recreate()
        awaitText("衣物档案")
        shot("03-item")
        ui.onNodeWithText("待洗").performScrollTo().performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        awaitText("我的衣橱")
        ui.onNodeWithText("搭配", substring = false).performClick()
        awaitText("搭配实验室")
        shot("04-outfits")
        ui.onNodeWithText("新建搭配").performClick()
        awaitText("搭配名称 *")
        ui.onNodeWithText("搭配名称 *").performTextInput(outfitName)
        ui.onNodeWithText("搜索衣物").performScrollTo().performTextInput(garmentName)
        hideKeyboard()
        ui.onNode(hasText(garmentName) and !hasSetTextAction()).performScrollTo().performClick()
        ui.onNodeWithText("保存").performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitText("搭配实验室")
        ui.onNodeWithText(outfitName).performScrollTo().performClick()
        awaitText("搭配档案")
        ui.onNodeWithText("安排到日历").performScrollTo().performClick()
        awaitText("安排穿搭计划")
        ui.onNodeWithText("保存").performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitText("搭配实验室")
        ui.onNodeWithText("日历", substring = false).performClick()
        awaitText("穿搭日历")
        shot("05-calendar")
        ui.onAllNodesWithText("确认实际穿着").onFirst().performScrollTo().performSemanticsAction(
            SemanticsActions.OnClick
        ) {
            it()
        }
        awaitText("保存实际穿着")
        ui.onNodeWithText("保存实际穿着").performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitText("穿搭日历")
        ui.onAllNodesWithText("已记录穿着").onFirst().performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("洗护", substring = false).performClick()
        awaitText("洗护工作台")
        shot("06-care")
        ui.onNodeWithText(garmentName).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("统计", substring = false).performClick()
        awaitText("衣橱观察")
        shot("07-statistics")
        ui.onNodeWithContentDescription("个人中心").performClick()
        awaitText("个人中心")
        shot("08-profile")
        ui.onNodeWithText("穿着历史", substring = false).performClick()
        awaitText("记录实际穿着")
        shot("09-history")
    }
}
