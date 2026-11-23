package com.wardrobe.atelier.presentation

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.wardrobe.atelier.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Coordinates user commands against an injected persistence port.
 * UI receives immutable snapshots; account and wardrobe writes share one operation coordinator.
 */
class WardrobeViewModel(
    private val repository: WardrobeRepository,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val capsuleUseCase: GenerateCapsuleUseCase = GenerateCapsuleUseCase(),
) : androidx.lifecycle.ViewModel() {
    private val operations = OperationCoordinator(viewModelScope, {
        val owner = repository.account()
        SessionSnapshot(owner, if (owner == null) Wardrobe() else repository.snapshot())
    }, ioDispatcher)
    val state = operations.state
    private val incomingImage = MutableStateFlow<Uri?>(null)
    val sharedImage = incomingImage.asStateFlow()
    val accountActions: AccountActions = AccountController(repository, operations) { incomingImage.value = null }

    fun queueSharedImage(uri: Uri) {
        incomingImage.value = uri
    }

    fun consumeSharedImage(uri: Uri) {
        if (incomingImage.value == uri) incomingImage.value = null
    }

    init { refreshSession() }

    fun refreshSession() = operations.refresh()
    fun error(text: String) = operations.error(text)
    fun notify(text: String) = operations.notify(text)
    fun clearError() = operations.clearError()
    fun clearMessage() = operations.clearMessage()

    private fun <T> perform(success: String?, after: (T) -> Unit, action: WardrobeRepository.() -> T) {
        operations.execute(success, after) { repository.action() }
    }

    fun saveQuickGarment(g: Garment, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Long -> after() }) { saveQuickGarment(g) }
    }
    fun saveQuickGarments(garments: List<Garment>, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: List<Long> -> after() }) { saveQuickGarments(garments) }
    }
    fun importImages(uris: List<Uri>, success: String? = null, after: (List<String>) -> Unit = {}) {
        perform(success, after) { importImages(uris.map { it.toString() }) }
    }
    fun discardDraftImages(paths: List<String>, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { discardDraftImages(paths) }
    }
    fun moveGarments(ids: List<Long>, location: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { moveGarments(ids, location) }
    }
    fun saveCapsule(c: Capsule, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Long -> after() }) { saveCapsule(c) }
    }
    fun setCapsulePacked(id: Long, garmentId: Long, checked: Boolean, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { setCapsulePacked(id, garmentId, checked) }
    }
    fun resetCapsulePacked(id: Long, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { resetCapsulePacked(id) }
    }
    fun deleteCapsule(id: Long, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { deleteCapsule(id) }
    }
    fun saveGarment(g: Garment, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Long -> after() }) { saveGarment(g) }
    }
    fun archive(id: Long, archived: Boolean, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { archive(id, archived) }
    }
    fun saveOutfit(o: Outfit, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Long -> after() }) { saveOutfit(o) }
    }
    fun deleteOutfit(id: Long, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { deleteOutfit(id) }
    }
    fun savePlan(p: Plan, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Long -> after() }) { savePlan(p) }
    }
    fun deletePlan(id: Long, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { deletePlan(id) }
    }
    fun saveWear(w: Wear, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Long -> after() }) { saveWear(w) }
    }
    fun voidWear(id: Long, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { voidWear(id) }
    }
    fun setCare(id: Long, state: Care, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { setCare(id, state) }
    }
    fun addLocation(name: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { addLocation(name) }
    }
    fun renameLocation(old: String, new: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { renameLocation(old, new) }
    }
    fun deleteLocation(name: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { deleteLocation(name) }
    }
    fun importImage(uri: Uri, success: String? = null, after: (String) -> Unit = {}) {
        perform(success, after) { importImage(uri.toString()) }
    }
    fun exportBackup(uri: Uri, passphrase: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { exportBackup(uri.toString(), passphrase) }
    }
    fun importBackup(uri: Uri, passphrase: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { importBackup(uri.toString(), passphrase) }
    }
    suspend fun generateCapsule(wardrobe: List<Garment>, count: Int, limit: Int, season: String, outerwear: Boolean): CapsuleResult =
        withContext(Dispatchers.Default) { capsuleUseCase(wardrobe, count, limit, season, outerwear) }

    override fun onCleared() {
        repository.close()
    }
}
