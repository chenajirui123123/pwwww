package com.wardrobe.atelier.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.os.bundleOf
import com.wardrobe.atelier.domain.InspirationRequest
import com.wardrobe.atelier.presentation.InspirationPresenter
import com.wardrobe.atelier.presentation.InspirationUiState

@Composable
internal fun rememberInspirationPresenter(seed: Long): InspirationPresenter {
    val scope = rememberCoroutineScope()
    val saver = remember(scope) {
        Saver<InspirationPresenter, Bundle>(
            save = { it.state.value.toBundle() },
            restore = { InspirationPresenter(it.toInspirationState(), scope) },
        )
    }
    return rememberSaveable(seed, saver = saver) {
        InspirationPresenter(InspirationUiState(locked = if (seed > 0) setOf(seed) else emptySet()), scope)
    }
}

private fun InspirationUiState.toBundle() = bundleOf(
    "season" to request.season, "structure" to request.structure, "mood" to request.mood,
    "outerwear" to request.outerwear, "bag" to request.bag,
    "locked" to locked.toLongArray(), "excluded" to excluded.toLongArray(), "picked" to picked.toLongArray(),
    "seen" to ArrayList(seen), "explanation" to explanation, "problem" to problem,
    "pending" to pending,
).apply {
    inventoryRevision?.let { putInt("revision", it) }
    replace?.let { putLong("replace", it) }
}

private fun Bundle.toInspirationState() = InspirationUiState(
    request = InspirationRequest(getString("season", "不限"), getString("structure", "自动"),
        getString("mood", "随心"), getBoolean("outerwear"), getBoolean("bag")),
    locked = getLongArray("locked")?.toSet().orEmpty(),
    excluded = getLongArray("excluded")?.toSet().orEmpty(),
    picked = getLongArray("picked")?.toList().orEmpty(),
    seen = getStringArrayList("seen").orEmpty(),
    explanation = getString("explanation", ""), problem = getString("problem", ""),
    inventoryRevision = if (containsKey("revision")) getInt("revision") else null,
    pending = getBoolean("pending", true), replace = if (containsKey("replace")) getLong("replace") else null,
)
