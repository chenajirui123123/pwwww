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

    private fun click(text: String) {
        val node = ui.onNodeWithText(text, substring = false)
        runCatching { node.performScrollTo() }
        node.assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { it() }
        ui.waitForIdle()
    }

    @Test
    fun lowFrictionWardrobeJourney() {
        awaitText("先逛逛示例衣橱")
        shot("01-welcome")
        click("先逛逛示例衣橱")
        awaitText("想找哪一件？")
        shot("02-wardrobe")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = com.wardrobe.atelier.data.LocalStore(context).use { it.snapshot() }
        click("录入衣物")
        awaitText("记下一件新衣")
        ui.onNodeWithText("名字（可不填，自动命名）").performScrollTo().performTextInput(garmentName)
        hideKeyboard()
        click("连衣裙")
        click("保存衣物")
        awaitText("想找哪一件？")
        ui.onNodeWithText("蓝色外套 / 宿舍 / 围巾").performTextInput(garmentName)
        hideKeyboard()
        ui.onNode(hasText(garmentName) and !hasSetTextAction()).performScrollTo().performClick()
        awaitText("衣物档案")
        ui.activityRule.scenario.recreate()
        awaitText("衣物档案")
        click("换位置了？更新一下 →")
        awaitText("现在放在哪里？")
        ui.onNodeWithText("位置，例如 宿舍衣柜 · 上层").performTextInput("宿舍衣柜 · 上层")
        hideKeyboard()
        click("更新位置")
        awaitText("宿舍衣柜 · 上层")
        shot("03-item-location")
        click("今天就想穿这件")
        awaitText("用这件，搭一套")
        awaitText("这一套，你已经拥有")
        ui.onNodeWithText("锁定 1 件 · 本轮跳过 0 件").assertExists()
        shot("04-seed-inspiration")
        click("收藏这套")
        awaitText("已收藏")
        click("去哪里拿这一套")
        awaitText("照着位置，顺手拿齐")
        ui.onNodeWithText(garmentName).performScrollTo().performClick()
        shot("05-pickup")
        ui.onNodeWithContentDescription("返回").performClick()
        awaitText("用这件，搭一套")
        ui.onNodeWithContentDescription("返回").performClick()
        awaitText("衣物档案")
        ui.onNodeWithContentDescription("返回").performClick()
        awaitText("想找哪一件？")
        ui.onNode(hasText(garmentName) and !hasSetTextAction()).assertExists()
        click("录入衣物")
        awaitText("记下一件新衣")
        org.junit.Assert.assertEquals("", ui.onNodeWithText("名字（可不填，自动命名）").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        ui.onNodeWithContentDescription("返回").performClick()
        awaitText("想找哪一件？")
        ui.onNodeWithText("我的", substring = false).performClick()
        awaitText("用到时，再来就好")
        shot("06-profile")
        click("买前看一眼")
        awaitText("购物前，先看看自己已有的。")
        shot("07-compare")
        ui.onNodeWithContentDescription("返回").performClick()
        awaitText("用到时，再来就好")
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("穿搭日历"))
        click("穿搭日历")
        awaitText("穿搭日历")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("我的", substring = false).assertExists()
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        awaitText("用到时，再来就好")
        val after = com.wardrobe.atelier.data.LocalStore(context).use { it.snapshot() }
        org.junit.Assert.assertEquals(before.wears, after.wears)
        org.junit.Assert.assertEquals(before.careLogs, after.careLogs)
        org.junit.Assert.assertEquals(before.garments.size + 1, after.garments.size)
        org.junit.Assert.assertTrue(after.outfits.size > before.outfits.size)
    }
}
