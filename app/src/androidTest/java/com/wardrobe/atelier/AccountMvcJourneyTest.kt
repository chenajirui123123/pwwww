package com.wardrobe.atelier

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wardrobe.atelier.data.LocalStore
import com.wardrobe.atelier.domain.Garment
import com.wardrobe.atelier.presentation.LanguagePreferences
import com.wardrobe.atelier.data.LocalLanguageSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** Exercises member A's real Compose -> Controller -> Repository -> SQLite flow. */
@RunWith(AndroidJUnit4::class)
class AccountMvcJourneyTest {
    private val ui = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            // This suite must never mutate the user's ordinary wardrobe installation.
            check(context.packageName.endsWith(".preview")) {
                "Run account UI checks with -PisolatedPreview on the preview package."
            }
            LocalStore(context).use { it.logout() }
            LanguagePreferences.initialize(LocalLanguageSettings(context))
            LanguagePreferences.select(false)
        }
        override fun after() { LocalStore(context).use { it.logout() } }
    }).around(ui)

    private fun waitForTag(tag: String) {
        ui.waitUntil(15000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
    }

    private fun waitForText(text: String) {
        ui.waitUntil(15000) { ui.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        val node = ui.onNode(matcher)
        if (runCatching { node.assertIsDisplayed() }.isFailure) {
            ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().indices.forEach { index ->
                runCatching {
                    ui.onAllNodes(hasScrollToIndexAction())[index].performScrollToNode(matcher)
                }
            }
            runCatching { node.performScrollTo() }
        }
    }

    private fun clickTag(tag: String) {
        scrollTo(hasTestTag(tag))
        ui.onNodeWithTag(tag).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        ui.waitForIdle()
    }

    private fun clickText(text: String) {
        scrollTo(hasText(text, substring = false))
        ui.onNodeWithText(text, substring = false).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        ui.waitForIdle()
    }

    private fun replace(tag: String, value: String) {
        scrollTo(hasTestTag(tag))
        ui.onNodeWithTag(tag).performTextReplacement(value)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        ui.runOnUiThread {
            WindowInsetsControllerCompat(ui.activity.window, ui.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        Thread.sleep(250)
        ui.waitForIdle()
    }

    private fun openAccountSettings() {
        clickText("我的")
        clickText("账号与设置")
        waitForText("个人中心")
    }

    private fun logout() {
        clickTag("profile.logout.open")
        waitForText("退出当前账号？")
        // The common confirmation dialog is shared with other modules.
        if (ui.onAllNodesWithTag("profile.logout.confirm").fetchSemanticsNodes().isNotEmpty()) {
            clickTag("profile.logout.confirm")
        } else {
            clickText("确认")
        }
        waitForTag("welcome.login")
    }

    private fun openLogin(username: String, password: String) {
        clickTag("welcome.login")
        waitForTag("account.username")
        replace("account.username", username)
        replace("account.password", password)
    }

    private fun register(username: String, nickname: String, password: String) {
        clickTag("account.toggleMode")
        waitForTag("account.confirmPassword")
        replace("account.username", username)
        replace("account.nickname", nickname)
        replace("account.password", password)
        replace("account.confirmPassword", password)
        clickTag("account.submit")
        waitForText("想找哪一件？")
    }

    @Test fun registerEditPasswordSwitchAccountsAndReturnWithoutDataLeak() {
        val suffix = System.currentTimeMillis().toString()
        val alice = "mvc_a_$suffix"
        val bob = "mvc_b_$suffix"
        val firstPassword = "Wardrobe123"
        val secondPassword = "Wardrobe456"
        val garmentName = "A 的蓝色衬衫 $suffix"

        waitForTag("welcome.login")
        openLogin(alice, firstPassword)

        // Sensitive inputs are not retained in saved instance state.
        ui.activityRule.scenario.recreate()
        waitForTag("account.password")
        assertEquals(
            "",
            ui.onNodeWithTag("account.password").fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text,
        )
        clickTag("account.toggleMode")
        waitForTag("account.confirmPassword")
        replace("account.username", alice)
        replace("account.nickname", "林同学")
        replace("account.password", firstPassword)
        replace("account.confirmPassword", firstPassword)
        clickTag("account.submit")
        waitForText("想找哪一件？")

        val aliceGarmentId = LocalStore(context).use { store ->
            assertEquals(alice, store.account()?.username)
            store.saveGarment(Garment(name = garmentName))
        }

        openAccountSettings()
        scrollTo(hasContentDescription("修改昵称"))
        ui.onNodeWithContentDescription("修改昵称").performClick()
        waitForTag("profile.rename.input")
        replace("profile.rename.input", "林同学·已更新")
        clickTag("profile.rename.save")
        waitForText("林同学·已更新")
        LocalStore(context).use { assertEquals("林同学·已更新", it.account()?.nickname) }

        clickTag("profile.password.open")
        waitForTag("profile.password.old")
        replace("profile.password.old", firstPassword)
        replace("profile.password.new", secondPassword)
        replace("profile.password.confirm", secondPassword)
        clickTag("profile.password.save")
        ui.waitUntil(15000) { ui.onAllNodesWithTag("profile.password.old").fetchSemanticsNodes().isEmpty() }
        ui.activityRule.scenario.recreate()
        waitForText("个人中心")
        logout()
        assertNull(LocalStore(context).use { it.account() })

        // The old password is rejected by the real PBKDF2-backed local account store.
        openLogin(alice, firstPassword)
        clickTag("account.submit")
        waitForText("账号或密码不正确")
        assertNull(LocalStore(context).use { it.account() })
        clickText("知道了")
        replace("account.password", secondPassword)
        clickTag("account.submit")
        waitForText("想找哪一件？")
        LocalStore(context).use { store ->
            assertEquals("林同学·已更新", store.account()?.nickname)
            assertEquals(aliceGarmentId, store.snapshot().garments.single().id)
        }

        openAccountSettings()
        logout()
        openLogin(bob, firstPassword)
        register(bob, "另一位同学", firstPassword)
        LocalStore(context).use { store ->
            assertEquals(bob, store.account()?.username)
            assertTrue(store.snapshot().garments.isEmpty())
            assertThrows(IllegalArgumentException::class.java) { store.archive(aliceGarmentId, true) }
        }
        ui.onAllNodesWithText(garmentName).assertCountEquals(0)

        openAccountSettings()
        logout()
        openLogin(alice, secondPassword)
        clickTag("account.submit")
        waitForText("想找哪一件？")
        LocalStore(context).use { store ->
            assertEquals(alice, store.account()?.username)
            assertEquals(garmentName, store.snapshot().garments.single().name)
        }
    }
}
