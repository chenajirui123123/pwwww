package com.wardrobe.atelier.presentation

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.wardrobe.atelier.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates user commands against an injected persistence port.
 * UI receives immutable snapshots; only this class may schedule repository operations.
 */
class WardrobeViewModel(
    private val repository: WardrobeRepository,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val capsuleUseCase: GenerateCapsuleUseCase = GenerateCapsuleUseCase(),
) : androidx.lifecycle.ViewModel() {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private val incomingImage = MutableStateFlow<Uri?>(null)
    val sharedImage = incomingImage.asStateFlow()

    fun queueSharedImage(uri: Uri) {
        incomingImage.value = uri
    }

    fun consumeSharedImage(uri: Uri) {
        if (incomingImage.value == uri) incomingImage.value = null
    }

    init {
        perform<Unit>(null, {}, {})
    }

    private fun <T> perform(success: String?, after: (T) -> Unit, action: WardrobeRepository.() -> T) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    val value = repository.action()
                    val account = repository.account()
                    Triple(value, account, if (account != null) repository.snapshot() else Wardrobe())
                }
                mutable.value = AppState(true, result.second, result.third, false, null, success)
                after(result.first)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(busy = false, loaded = true, error = e.message ?: "操作失败，请重试")
            }
        }
    }


    fun error(text: String) {
        mutable.value = mutable.value.copy(error = text)
    }

    fun notify(text: String) {
        mutable.value = mutable.value.copy(message = text)
    }

    fun clearError() {
        mutable.value = mutable.value.copy(error = null)
    }

    fun clearMessage() {
        mutable.value = mutable.value.copy(message = null)
    }

    fun register(username: String, pass: String, nickname: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Account -> after() }) { register(username, pass, nickname) }
    }
    fun openLocalWardrobe(success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Account -> after() }) { openLocalWardrobe() }
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
    fun login(username: String, pass: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Account -> after() }) { login(username, pass) }
    }
    fun logout(success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { logout() }
    }
    fun rename(nickname: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { rename(nickname) }
    }
    fun changePassword(old: String, new: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { changePassword(old, new) }
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
    fun demo(success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Account -> after() }) { demo() }
    }
    fun exportBackup(uri: Uri, passphrase: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { exportBackup(uri.toString(), passphrase) }
    }
    fun importBackup(uri: Uri, passphrase: String, success: String? = null, after: () -> Unit = {}) {
        perform(success, { _: Unit -> after() }) { importBackup(uri.toString(), passphrase) }
    }
    fun authenticate(registering: Boolean, username: String, password: String, nickname: String) {
        if (registering) register(username, password, nickname, "欢迎来到衣序")
        else login(username, password, "欢迎来到衣序")
    }
    suspend fun generateCapsule(wardrobe: List<Garment>, count: Int, limit: Int, season: String, outerwear: Boolean): CapsuleResult =
        withContext(Dispatchers.Default) { capsuleUseCase(wardrobe, count, limit, season, outerwear) }

    override fun onCleared() {
        repository.close()
    }
}
