package com.wardrobe.atelier.presentation

import com.wardrobe.atelier.domain.SessionSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Serializes account and wardrobe commands against the same local owner and publishes one coherent snapshot. */
internal class OperationCoordinator(
    private val scope: CoroutineScope,
    private val loadSnapshot: () -> SessionSnapshot,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()

    fun refresh() = schedule(null, {}, readingOnly = true) { }

    fun <T> execute(success: String? = null, after: (T) -> Unit = {}, action: () -> T) =
        schedule(success, after, readingOnly = false, action)

    private fun <T> schedule(success: String?, after: (T) -> Unit, readingOnly: Boolean, action: () -> T) {
        while (true) {
            val before = mutable.value
            if (before.busy || (before.needsReload && !readingOnly)) return
            if (mutable.compareAndSet(before, before.copy(busy = true, error = null, message = null))) break
        }
        scope.launch {
            val (outcome, snapshot) = try {
                withContext(worker) {
                    val result = try {
                        Result.success(action())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Result.failure<T>(failure)
                    }
                    result to readCurrentSession()
                }
            } catch (cancelled: CancellationException) {
                mutable.value = AppState(loaded = true, needsReload = true)
                throw cancelled
            }
            if (snapshot == null) {
                // The operation may have switched owner or committed a write. Only reload is allowed.
                mutable.value = AppState(loaded = true, needsReload = true, error = "暂时无法读取衣橱，请重新读取。")
                return@launch
            }
            val failure = outcome.exceptionOrNull()
            mutable.value = AppState(
                loaded = true, account = snapshot.account, data = snapshot.data,
                error = if (failure == null) null else failure.message ?: "操作失败，请重试",
                message = if (failure == null) success else null,
            )
            outcome.onSuccess(after)
        }
    }

    private fun readCurrentSession(): SessionSnapshot? {
        repeat(2) {
            try {
                return loadSnapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Retry the read only. Replaying the command could duplicate an already committed change.
            }
        }
        return null
    }

    fun error(text: String) = mutable.update { it.copy(error = text) }
    fun notify(text: String) = mutable.update { it.copy(message = text) }
    fun clearError() = mutable.update { it.copy(error = null) }
    fun clearMessage() = mutable.update { it.copy(message = null) }
}
