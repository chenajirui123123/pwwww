package com.wardrobe.atelier.presentation

import com.wardrobe.atelier.domain.Garment
import com.wardrobe.atelier.domain.GenerateOutfitUseCase
import com.wardrobe.atelier.domain.InspirationRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InspirationUiState(
    val request: InspirationRequest = InspirationRequest(),
    val locked: Set<Long> = emptySet(),
    val excluded: Set<Long> = emptySet(),
    val picked: List<Long> = emptyList(),
    val seen: List<String> = emptyList(),
    val explanation: String = "",
    val problem: String = "",
    val inventoryRevision: Int? = null,
    val pending: Boolean = true,
    val replace: Long? = null,
)

/** One session per navigation entry. Events and state commits run on the screen's main scope. */
class InspirationPresenter(
    initial: InspirationUiState,
    private val scope: CoroutineScope,
    private val computation: CoroutineDispatcher = Dispatchers.Default,
) {
    private val generateOutfit = GenerateOutfitUseCase()
    private val mutable = MutableStateFlow(initial)
    val state = mutable.asStateFlow()

    private var wardrobe: List<Garment>? = null
    private var calculation: Job? = null

    fun updateInventory(garments: List<Garment>) {
        wardrobe = garments
        val current = state.value
        // Location, notes and wear logs do not affect an outfit. Keep it when only those change.
        val revision = garments.map { listOf(it.id, it.category, it.color, it.season, it.style, it.archived) }.hashCode()
        if (current.inventoryRevision == revision) {
            if (current.pending && calculation?.isActive != true) generate(current)
            return
        }
        val active = garments.filterNot { it.archived }.map { it.id }.toSet()
        generate(current.copy(
            inventoryRevision = revision,
            locked = current.locked.intersect(active),
            excluded = current.excluded.intersect(active),
            picked = current.picked.filter { it in active },
            replace = null,
        ))
    }

    fun changeRequest(update: (InspirationRequest) -> InspirationRequest) {
        val current = state.value
        generate(current.copy(request = update(current.request), replace = null))
    }

    fun reroll() = generate(state.value.copy(replace = null))

    fun toggleLock(id: Long) {
        val current = state.value
        if (current.pending || id !in current.picked) return
        val pins = if (id in current.locked) current.locked - id else current.locked + id
        mutable.value = current.copy(locked = pins)
    }

    fun replacePiece(id: Long) {
        val current = state.value
        if (!current.pending && id in current.picked && id !in current.locked) generate(current.copy(replace = id))
    }

    fun skip(id: Long) {
        val current = state.value
        if (!current.pending && id in current.picked) {
            generate(current.copy(locked = current.locked - id, excluded = current.excluded + id, replace = null))
        }
    }

    fun resetConstraints() = generate(state.value.copy(locked = emptySet(), excluded = emptySet(), replace = null))

    private fun generate(next: InspirationUiState) {
        calculation?.cancel()
        mutable.value = next.copy(pending = true, problem = "")
        val garments = wardrobe ?: return
        calculation = scope.launch {
            val completed = try {
                val outfit = withContext(computation) {
                    val byId = garments.associateBy { it.id }
                    generateOutfit(
                        garments, next.request, next.locked, next.excluded, next.seen.toSet(),
                        next.picked.mapNotNull(byId::get), next.replace,
                    )
                }
                next.copy(
                    picked = outfit.items.map { it.id },
                    seen = (next.seen + outfit.signature).takeLast(24),
                    explanation = outfit.notes.joinToString(" · "),
                    problem = if (outfit.repeated) "当前条件下的组合已经看过了，可以换个条件，或解锁一件再试。" else "",
                )
            } catch (failure: IllegalArgumentException) {
                next.copy(
                    picked = if (next.replace == null) emptyList() else next.picked,
                    problem = if (next.replace != null) "这一件暂时没有合适的替换。其他单品保留原样。"
                              else failure.message ?: "暂时无法组合",
                )
            }
            ensureActive()
            mutable.value = completed.copy(pending = false, replace = null)
        }
    }
}
