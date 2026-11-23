package com.wardrobe.atelier

import com.wardrobe.atelier.presentation.AccountController
import com.wardrobe.atelier.presentation.OperationCoordinator
import com.wardrobe.atelier.domain.AccountRepository
import com.wardrobe.atelier.domain.Account
import com.wardrobe.atelier.domain.Garment
import com.wardrobe.atelier.domain.SessionSnapshot
import com.wardrobe.atelier.domain.Wardrobe
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Tests Controller coordination independently of Compose, Android and SQLite. */
class AccountControllerTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @After fun closeScopes() { scopes.forEach { it.cancel() } }

    private data class Fixture(
        val repository: FakeAccounts,
        val operations: OperationCoordinator,
        val controller: AccountController,
    )

    private fun fixture(onLogout: () -> Unit = {}): Fixture {
        val repository = FakeAccounts()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }
        val operations = OperationCoordinator(scope, repository::snapshot, Dispatchers.Unconfined)
        return Fixture(repository, operations, AccountController(repository, operations, onLogout))
    }

    @Test fun registerPublishesPersistedAccountAndCompletesLoading() {
        val f = fixture()
        f.controller.register(" Alice ", "password1", " Alice Name ", "password1")
        val state = f.operations.state.value
        assertTrue(state.loaded)
        assertFalse(state.busy)
        assertNull(state.error)
        assertEquals("alice", state.account?.username)
        assertEquals("Alice Name", state.account?.nickname)
        assertEquals(1, f.repository.registrationWrites)
    }

    @Test fun mismatchedConfirmationDoesNotCallPersistence() {
        val f = fixture()
        f.controller.register("alice", "password1", "Alice", "different")
        assertEquals(0, f.repository.registrationWrites)
        assertNotNull(f.operations.state.value.error)
        assertNull(f.operations.state.value.account)
        assertFalse(f.operations.state.value.busy)
    }

    @Test fun wrongPasswordRetainsCurrentIdentityAndData() {
        val f = fixture()
        f.controller.register("alice", "password1", "Alice", "password1")
        val expected = f.operations.state.value
        f.controller.login("alice", "incorrect")
        assertEquals(expected.account, f.operations.state.value.account)
        assertEquals(expected.data, f.operations.state.value.data)
        assertNotNull(f.operations.state.value.error)
        assertFalse(f.operations.state.value.busy)
    }

    @Test fun nicknameUpdateRefreshesSnapshotAndRunsSuccessCallback() {
        val f = fixture()
        f.controller.register("alice", "password1", "Alice", "password1")
        var callback = false
        f.controller.updateNickname(" New Name ") { callback = true }
        assertTrue(callback)
        assertEquals("New Name", f.operations.state.value.account?.nickname)
        callback = false
        f.controller.updateNickname("   ") { callback = true }
        assertFalse(callback)
        assertEquals("New Name", f.operations.state.value.account?.nickname)
    }

    @Test fun passwordChangeLeavesOldPasswordInvalidAndNewPasswordUsable() {
        val f = fixture()
        f.controller.register("alice", "password1", "Alice", "password1")
        f.controller.changePassword("password1", "newpass23", "newpass23")
        assertNull(f.operations.state.value.error)
        f.controller.logout()
        f.controller.login("alice", "password1")
        assertNotNull(f.operations.state.value.error)
        assertNull(f.operations.state.value.account)
        f.controller.login("alice", "newpass23")
        assertNull(f.operations.state.value.error)
        assertEquals("alice", f.operations.state.value.account?.username)
    }

    @Test fun logoutPublishesEmptyAccountAndWardrobeAndRunsCallback() {
        var callback = false
        val f = fixture { callback = true }
        f.controller.register("alice", "password1", "Alice", "password1")
        assertFalse(f.operations.state.value.data.garments.isEmpty())
        f.controller.logout()
        assertTrue(callback)
        assertNull(f.operations.state.value.account)
        assertEquals(Wardrobe(), f.operations.state.value.data)
    }

    @Test fun accountSwitchRefreshesOwnerScopedSnapshot() {
        val f = fixture()
        f.controller.register("alice", "password1", "Alice", "password1")
        val alice = f.operations.state.value.data.garments.single()
        f.controller.logout()
        f.controller.register("bobby", "password2", "Bob", "password2")
        val bob = f.operations.state.value.data.garments.single()
        assertNotEquals(alice.id, bob.id)
        assertFalse(f.operations.state.value.data.garments.any { it.id == alice.id })
        f.controller.logout()
        f.controller.login("alice", "password1")
        assertEquals(alice, f.operations.state.value.data.garments.single())
    }

    @Test fun sharedCoordinatorRejectsDuplicateSubmissionWhileBusy() {
        val dispatcher = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }
        val operations = OperationCoordinator(scope, { SessionSnapshot(null, Wardrobe()) }, dispatcher)
        var writes = 0
        operations.execute(action = { writes++ })
        assertTrue(operations.state.value.busy)
        operations.execute(action = { writes += 100 })
        dispatcher.drain()
        assertEquals(1, writes)
        assertFalse(operations.state.value.busy)
        assertTrue(operations.state.value.loaded)
    }

    @Test fun failedSnapshotAfterIdentityChangeNeverDisplaysPreviousOwnersData() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }
        var active = Account(1, "alice", "Alice")
        var failReads = false
        val operations = OperationCoordinator(scope, {
            if (failReads) error("Database read failed")
            SessionSnapshot(active, Wardrobe(garments = listOf(Garment(id = active.id, name = active.username))))
        }, Dispatchers.Unconfined)
        operations.execute(action = {})
        assertEquals("alice", operations.state.value.account?.username)
        operations.execute(action = {
            active = Account(2, "bobby", "Bob")
            failReads = true
        })
        assertNull(operations.state.value.account)
        assertEquals(Wardrobe(), operations.state.value.data)
        assertNotNull(operations.state.value.error)
        assertTrue(operations.state.value.needsReload)
        var writes = 0
        operations.execute { writes++ }
        assertEquals(0, writes)
        failReads = false
        operations.refresh()
        assertFalse(operations.state.value.needsReload)
        assertEquals("bobby", operations.state.value.data.garments.single().name)
        assertFalse(operations.state.value.busy)
    }

    @Test fun successfulRecoveryPublishesNewIdentityInsteadOfPreviousOwnersData() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }
        var active = Account(1, "alice", "Alice")
        var failOnce = false
        val operations = OperationCoordinator(scope, {
            if (failOnce) { failOnce = false; error("Temporary database read failure") }
            SessionSnapshot(active, Wardrobe(garments = listOf(Garment(id = active.id, name = active.username))))
        }, Dispatchers.Unconfined)
        operations.execute(action = {})
        var successCallback = false
        var writes = 0
        operations.execute(after = { successCallback = true }, action = {
            writes++
            active = Account(2, "bobby", "Bob")
            failOnce = true
        })
        assertEquals(1, writes)
        assertTrue(successCallback)
        assertEquals("bobby", operations.state.value.account?.username)
        assertEquals("bobby", operations.state.value.data.garments.single().name)
        assertFalse(operations.state.value.busy)
        assertNull(operations.state.value.error)
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks += block }
        fun drain() { while (true) (tasks.poll() ?: break).run() }
    }

    private class FakeAccounts : AccountRepository {
        private data class Entry(var account: Account, var password: String, val wardrobe: Wardrobe)
        private val entries = linkedMapOf<String, Entry>()
        private var current: Entry? = null
        var registrationWrites = 0
            private set
        private fun normalized(username: String) = username.trim().lowercase(Locale.ROOT)

        override fun account(): Account? = current?.account
        override fun register(username: String, pass: String, nickname: String): Account {
            val key = normalized(username)
            require(key !in entries) { "Account already exists" }
            registrationWrites++
            val id = entries.size.toLong() + 1
            val account = Account(id, key, nickname.trim())
            current = Entry(account, pass, Wardrobe(garments = listOf(Garment(id = id, name = "Owned by $key"))))
            entries[key] = current!!
            return account
        }
        override fun login(username: String, pass: String): Account {
            val candidate = entries[normalized(username)]
            require(candidate != null && candidate.password == pass) { "Incorrect account or password" }
            current = candidate
            return candidate.account
        }
        override fun logout() { current = null }
        override fun rename(nickname: String) {
            val entry = checkNotNull(current)
            entry.account = entry.account.copy(nickname = nickname.trim())
        }
        override fun changePassword(old: String, new: String) {
            val entry = checkNotNull(current)
            require(entry.password == old) { "Incorrect current password" }
            entry.password = new
        }
        override fun openLocalWardrobe(): Account = openSpace("wardrobe_local", "Local")
        override fun demo(): Account = openSpace("wardrobe_demo", "Demo")
        private fun openSpace(username: String, name: String): Account {
            val entry = entries[username]
            if (entry != null) { current = entry; return entry.account }
            return register(username, "internal-only", name)
        }
        fun snapshot(): SessionSnapshot = SessionSnapshot(account(), current?.wardrobe ?: Wardrobe())
    }
}
