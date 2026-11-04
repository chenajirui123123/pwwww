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
        Thread.sleep(450) // Surface/IME transitions are separate from Compose's test clock.
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(ui.activity.getExternalFilesDir(null), "qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun click(text: String) {
        val node = ui.onNodeWithText(text, substring = false)
        if (runCatching { node.assertIsDisplayed() }.isFailure) {
            ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().indices.forEach { index ->
                runCatching {
                    ui.onAllNodes(hasScrollToIndexAction())[index].performScrollToNode(
                        hasText(text, substring = false)
                    )
                }
            }
            runCatching { node.performScrollTo() }
        }
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
        org.junit.Assert.assertEquals(
            "",
            ui.onNodeWithText("名字（可不填，自动命名）")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
                .text,
        )
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
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("轻装出门"))
        click("轻装出门")
        awaitText("组合一份行李胶囊")
        shot("08-capsule-entry")
        click("组合一份行李胶囊")
        awaitText("看看怎么带")
        click("看看怎么带")
        awaitText("保存这份行李胶囊")
        shot("09-capsule-build")
        click("保存这份行李胶囊")
        awaitText("轻装出门")
        click("周末轻装")
        awaitText("拿衣清单")
        ui.onAllNodes(isToggleable()).onFirst().performClick()
        ui.waitUntil(5000) {
            com.wardrobe.atelier.data.LocalStore(context).use {
                it.snapshot().capsules.single().packed.size == 1
            }
        }
        ui.activityRule.scenario.recreate()
        awaitText("拿衣清单")
        ui.onAllNodes(isToggleable()).onFirst().assertIsOn()
        shot("10-capsule-checklist")
        click("穿搭组合")
        awaitText("组合 1")
        ui.onNodeWithContentDescription("返回").performClick()
        awaitText("轻装出门")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("穿搭日历"))
        click("穿搭日历")
        awaitText("穿搭日历")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("我的", substring = false).assertExists()
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        awaitText("用到时，再来就好")
        ui.onNodeWithText("衣橱", substring = false).performClick()
        awaitText("想找哪一件？")
        click("录入衣物")
        awaitText("记下一件新衣")
        ui.onNodeWithText("名字（可不填，自动命名）").performScrollTo().performTextInput("原有未保存草稿")
        hideKeyboard()
        val resolver = context.contentResolver
        val shareImage =
            resolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                android.content.ContentValues().apply {
                    put(
                        android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                        "Yixu-share-${System.nanoTime()}.png",
                    )
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                },
            )!!
        try {
            resolver.openOutputStream(shareImage)!!.use { output ->
                Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(android.graphics.Color.rgb(40, 196, 189)) }
                    .compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            val request =
                android.content.Intent(context, MainActivity::class.java).apply {
                    action = android.content.Intent.ACTION_SEND
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, shareImage)
                    addFlags(
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            ui.runOnUiThread { ui.activity.startActivity(request) }
            awaitText("用这张图片记一件衣服")
            click("开始录入")
            awaitText("记下一件新衣")
            awaitText("更换照片")
            ui.onNodeWithText("名字（可不填，自动命名）").performScrollTo().performTextInput("分享图片测试衣物")
            hideKeyboard()
            click("保存衣物")
            awaitText("记下一件新衣")
            ui.onNodeWithText("原有未保存草稿").assertExists()
            ui.onNodeWithContentDescription("返回").performClick()
            awaitText("离开这次录入？")
            click("确认")
            awaitText("想找哪一件？")
            val shared =
                com.wardrobe.atelier.data.LocalStore(context).use {
                    it.snapshot().garments.first { g -> g.name == "分享图片测试衣物" }
                }
            org.junit.Assert.assertTrue(java.io.File(shared.image).isFile)
        } finally {
            resolver.delete(shareImage, null, null)
        }
        ui.onNodeWithContentDescription("清空搜索").performClick()
        click("整理位置")
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("取消整理").assertDoesNotExist()
        click("整理位置")
        click("全选当前结果")
        click("移至…")
        awaitText("现在放在哪里？")
        ui.onNodeWithText("位置，例如 宿舍衣柜 · 上层").performTextInput("换季衣柜")
        hideKeyboard()
        click("更新位置")
        ui.waitUntil(5000) {
            com.wardrobe.atelier.data.LocalStore(context).use {
                it.snapshot().active.all { g -> g.location == "换季衣柜" }
            }
        }
        shot("11-bulk-location")
        val batchUris =
            (1..2).map { index ->
                resolver
                    .insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        android.content.ContentValues().apply {
                            put(
                                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                                "Yixu-batch-$index-${System.nanoTime()}.png",
                            )
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                        },
                    )!!
                    .also { uri ->
                        resolver.openOutputStream(uri)!!.use { output ->
                            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                                .apply {
                                    eraseColor(
                                        if (index == 1) android.graphics.Color.BLUE
                                        else android.graphics.Color.GREEN
                                    )
                                }
                                .compress(Bitmap.CompressFormat.PNG, 100, output)
                        }
                    }
            }
        val selection =
            android.content.Intent().apply {
                clipData =
                    android.content.ClipData.newUri(resolver, "QA", batchUris[0]).also {
                        it.addItem(android.content.ClipData.Item(batchUris[1]))
                    }
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor =
            instrumentation.addMonitor(
                android.content.IntentFilter(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    addDataType("*/*")
                },
                android.app.Instrumentation.ActivityResult(
                    android.app.Activity.RESULT_OK,
                    selection,
                ),
                true,
            )
        try {
            ui.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            click("批量录入")
            awaitText("一次记下几件")
            click("选择多张照片")
            awaitText("保存这 2 件衣物")
            ui.activityRule.scenario.recreate()
            awaitText("保存这 2 件衣物")
            shot("12-batch-entry")
            click("保存这 2 件衣物")
            awaitText("想找哪一件？")
        } finally {
            instrumentation.removeMonitor(monitor)
            batchUris.forEach { resolver.delete(it, null, null) }
        }
        val after = com.wardrobe.atelier.data.LocalStore(context).use { it.snapshot() }
        org.junit.Assert.assertEquals(before.wears, after.wears)
        org.junit.Assert.assertEquals(before.careLogs, after.careLogs)
        org.junit.Assert.assertEquals(before.garments.size + 4, after.garments.size)
        org.junit.Assert.assertTrue(after.outfits.size > before.outfits.size)
    }
}
