package com.wardrobe.atelier

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wardrobe.atelier.domain.Account
import com.wardrobe.atelier.domain.Garment
import com.wardrobe.atelier.domain.Wardrobe
import com.wardrobe.atelier.domain.WardrobeRepository
import com.wardrobe.atelier.presentation.LanguagePreferences
import com.wardrobe.atelier.presentation.WardrobeViewModel
import com.wardrobe.atelier.ui.WardrobeApp
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionRecoveryUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun retryButtonReloadsOnlyTheNewOwnerAfterReadFailures() {
        val unreadable = AtomicBoolean(false)
        val logins = AtomicInteger(0)
        var account = Account(1, "alice", "Alice")
        val repo = Proxy.newProxyInstance(WardrobeRepository::class.java.classLoader, arrayOf(WardrobeRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "account" -> account
                "snapshot" -> {
                    check(!unreadable.get()) { "Read unavailable" }
                    Wardrobe(garments = listOf(Garment(id = account.id, name = account.nickname)))
                }
                "login" -> {
                    logins.incrementAndGet()
                    unreadable.set(true)
                    Account(2, "bobby", "Bob").also { account = it }
                }
                "close" -> null
                else -> error("Unexpected call: ${method.name}")
            }
        } as WardrobeRepository
        val owner = ViewModelStore()
        lateinit var vm: WardrobeViewModel
        ui.runOnUiThread { vm = WardrobeViewModel(repo); owner.put("wardrobe", vm) }
        try {
            ui.setContent { WardrobeApp(vm) }
            ui.waitUntil(10000) { vm.state.value.data.garments.singleOrNull()?.name == "Alice" }
            ui.runOnIdle { vm.accountActions.login("bobby", "password2") }
            val retry = LanguagePreferences.text("重新读取衣橱")
            ui.waitUntil(10000) { ui.onAllNodesWithText(retry).fetchSemanticsNodes().isNotEmpty() }
            ui.onAllNodesWithText(LanguagePreferences.text("想找哪一件？")).assertCountEquals(0)
            assertNull(vm.state.value.account)
            assertTrue(vm.state.value.data.garments.isEmpty())
            unreadable.set(false)
            ui.onNodeWithText(retry).performClick()
            ui.waitUntil(10000) { vm.state.value.data.garments.singleOrNull()?.name == "Bob" }
            assertEquals("bobby", vm.state.value.account?.username)
            assertEquals(1, logins.get())
        } finally {
            ui.runOnUiThread { owner.clear() }
        }
    }
}
