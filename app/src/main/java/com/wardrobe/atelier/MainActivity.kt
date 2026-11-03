package com.wardrobe.atelier

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.atelier.data.LocalStore
import com.wardrobe.atelier.domain.*
import com.wardrobe.atelier.ui.WardrobeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WardrobeApp() }
    }
}

data class AppState(
    val loaded: Boolean = false,
    val account: Account? = null,
    val data: Wardrobe = Wardrobe(),
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class WardrobeViewModel(application: Application) : AndroidViewModel(application) {
    val store = LocalStore(application)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()

    init {
        perform(null) {}
    }

    fun perform(success: String? = "已保存", after: () -> Unit = {}, action: LocalStore.() -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val (account, data) =
                    withContext(Dispatchers.IO) {
                        store.action()
                        val a = store.account()
                        a to if (a != null) store.snapshot() else Wardrobe()
                    }
                mutable.value = AppState(true, account, data, false, null, success)
                after()
            } catch (e: Exception) {
                mutable.value =
                    mutable.value.copy(loaded = true, busy = false, error = e.message ?: "操作失败，请重试")
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

    override fun onCleared() {
        store.close()
    }
}
