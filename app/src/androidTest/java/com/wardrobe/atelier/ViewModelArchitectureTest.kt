package com.wardrobe.atelier

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wardrobe.atelier.domain.*
import com.wardrobe.atelier.presentation.WardrobeViewModel
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the presentation layer with an injected fake; no SQLite, files or UI involved. */
@RunWith(AndroidJUnit4::class)
class ViewModelArchitectureTest {
    private class Fake {
        @Volatile var data = Wardrobe()
        @Volatile var failure: String? = null
        var entered: CountDownLatch? = null
        var release: CountDownLatch? = null
        val saves = AtomicInteger()
        val logouts = AtomicInteger()
        var closed = false
        val repository = Proxy.newProxyInstance(
            WardrobeRepository::class.java.classLoader,
            arrayOf(WardrobeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "account" -> Account(1, "test", "Test")
                "snapshot" -> data
                "saveGarment" -> {
                    saves.incrementAndGet()
                    entered?.countDown()
                    check(release?.await(10, TimeUnit.SECONDS) != false)
                    failure?.let { throw IllegalArgumentException(it) }
                    val g = (args!![0] as Garment).copy(id = 1)
                    data = data.copy(garments = listOf(g))
                    1L
                }
                "logout" -> { logouts.incrementAndGet(); Unit }
                "close" -> { closed = true; null }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as WardrobeRepository
    }

    private fun main(action: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!predicate()) {
            check(System.nanoTime() < deadline) { "State transition timed out" }
            Thread.sleep(10)
        }
    }

    private fun withModel(fake: Fake, action: (WardrobeViewModel) -> Unit) {
        val owner = ViewModelStore()
        lateinit var vm: WardrobeViewModel
        main { vm = WardrobeViewModel(fake.repository); owner.put("test", vm) }
        try {
            await { vm.state.value.loaded }
            action(vm)
        } finally {
            fake.release?.countDown()
            main { owner.clear() }
        }
        assertTrue(fake.closed)
    }

    @Test fun successfulSavePublishesSnapshotThenCallback() {
        val fake = Fake()
        val done = CountDownLatch(1)
        withModel(fake) { vm ->
            main {
                vm.saveGarment(Garment(name = "Original user name"), "Saved", {
                    assertEquals("Original user name", vm.state.value.data.garments.single().name)
                    assertFalse(vm.state.value.busy)
                    done.countDown()
                })
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals("Saved", vm.state.value.message)
            assertNull(vm.state.value.error)
            assertEquals(1, fake.saves.get())
        }
    }

    @Test fun failedSavePreservesDataAndDoesNotNavigate() {
        val fake = Fake().apply {
            data = Wardrobe(garments = listOf(Garment(id = 7, name = "Keep me")))
            failure = "Validation failed"
        }
        var navigated = false
        withModel(fake) { vm ->
            val before = vm.state.value.data
            main { vm.saveGarment(Garment(name = "Rejected"), after = { navigated = true }) }
            await { vm.state.value.error != null }
            assertEquals(before, vm.state.value.data)
            assertEquals("Validation failed", vm.state.value.error)
            assertFalse(vm.state.value.busy)
            assertFalse(navigated)
        }
    }

    @Test fun accountAndWardrobeCommandsShareOneBusyState() {
        val fake = Fake().apply { entered = CountDownLatch(1); release = CountDownLatch(1) }
        withModel(fake) { vm ->
            main { vm.saveGarment(Garment(name = "Current owner")) }
            assertTrue(fake.entered!!.await(10, TimeUnit.SECONDS))
            main { vm.accountActions.logout() }
            fake.release!!.countDown()
            await { !vm.state.value.busy }
            assertEquals(0, fake.logouts.get())
            assertEquals(1, fake.saves.get())
        }
    }

    @Test fun busyStateRejectsDuplicateSubmission() {
        val fake = Fake().apply { entered = CountDownLatch(1); release = CountDownLatch(1) }
        withModel(fake) { vm ->
            main { vm.saveGarment(Garment(name = "First")) }
            assertTrue(fake.entered!!.await(10, TimeUnit.SECONDS))
            assertTrue(vm.state.value.busy)
            main { vm.saveGarment(Garment(name = "Second")) }
            fake.release!!.countDown()
            await { !vm.state.value.busy }
            assertEquals(1, fake.saves.get())
            assertEquals("First", vm.state.value.data.garments.single().name)
        }
    }
}
