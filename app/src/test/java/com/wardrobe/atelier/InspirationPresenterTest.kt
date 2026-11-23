package com.wardrobe.atelier

import com.wardrobe.atelier.domain.Garment
import com.wardrobe.atelier.presentation.InspirationPresenter
import com.wardrobe.atelier.presentation.InspirationUiState
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class InspirationPresenterTest {
    private val dress = Garment(id = 7, category = "连衣裙", season = "夏季")
    private val top = Garment(id = 8, category = "上装")
    private val pants = Garment(id = 9, category = "下装")
    private val garments = listOf(dress, top, pants)
    private fun session(state: InspirationUiState = InspirationUiState(), worker: CoroutineDispatcher = Dispatchers.Unconfined) =
        InspirationPresenter(state, CoroutineScope(Dispatchers.Unconfined), worker)

    @Test fun conflictingFiltersCanBeResetEvenWhenThereIsNoOutfit() {
        val presenter = session(InspirationUiState(locked = setOf(dress.id)))
        presenter.updateInventory(garments)
        presenter.changeRequest { it.copy(season = "冬季") }
        assertTrue(presenter.state.value.picked.isEmpty())
        assertTrue(presenter.state.value.problem.isNotBlank())
        presenter.resetConstraints()
        assertEquals(listOf(top.id, pants.id), presenter.state.value.picked)
        assertEquals("冬季", presenter.state.value.request.season)
    }

    @Test fun restoredOutfitSurvivesLocationChangeAndArchivedPinsAreReleased() {
        val seeded = session(InspirationUiState(locked = setOf(dress.id)))
        seeded.updateInventory(garments)
        val saved = seeded.state.value
        val restored = session(saved)
        restored.updateInventory(garments.map { it.copy(location = "新家") })
        assertEquals(saved, restored.state.value)
        restored.updateInventory(listOf(dress.copy(archived = true), top, pants))
        assertTrue(restored.state.value.locked.isEmpty())
        assertEquals(listOf(top.id, pants.id), restored.state.value.picked)
    }

    @Test fun failedShoeReplacementKeepsTheDisplayedOutfitAndItsHistory() {
        val shoe = Garment(id = 10, category = "鞋履")
        val presenter = session()
        presenter.updateInventory(listOf(dress, shoe))
        val original = presenter.state.value
        presenter.replacePiece(shoe.id)
        val after = presenter.state.value
        assertEquals(original.picked, after.picked)
        assertEquals(original.seen, after.seen)
        assertTrue(after.problem.isNotBlank())
    }

    @Test fun rapidFiltersPublishOnlyTheLastResult() {
        val work = ArrayDeque<Runnable>()
        val queued = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { work.addLast(block) }
        }
        val presenter = session(worker = queued)
        presenter.updateInventory(garments)
        presenter.changeRequest { it.copy(structure = "上下装") }
        presenter.changeRequest { it.copy(structure = "连衣裙") }
        assertTrue(presenter.state.value.pending)
        while (work.isNotEmpty()) work.removeFirst().run()
        val result = presenter.state.value
        assertFalse(result.pending)
        assertEquals(listOf(dress.id), result.picked)
        assertEquals(listOf(dress.id.toString()), result.seen)
    }

    @Test fun pendingSwapResumesOnRestore() {
        val alternate = top.copy(id = 11)
        val inventory = listOf(top, pants, alternate)
        val first = session()
        first.updateInventory(inventory)
        val original = first.state.value
        val selectedTop = original.picked.first()
        val restored = session(original.copy(pending = true, replace = selectedTop))
        restored.updateInventory(inventory)
        val result = restored.state.value
        assertFalse(result.pending)
        assertNull(result.replace)
        assertTrue(pants.id in result.picked)
        assertFalse(selectedTop in result.picked)
    }
}
