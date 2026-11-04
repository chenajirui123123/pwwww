package com.wardrobe.atelier

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
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
    private val wardrobeModel: WardrobeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receiveShare(intent)
        setContent { WardrobeApp(wardrobeModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    private fun receiveShare(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java)
        if (
            incoming.type !in setOf("image/jpeg", "image/png", "image/webp") ||
                uri?.scheme != "content"
        ) {
            wardrobeModel.error("请分享一张 JPEG、PNG 或 WebP 图片")
        } else wardrobeModel.queueSharedImage(uri)
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
    private val incomingImage = MutableStateFlow<Uri?>(null)
    val sharedImage = incomingImage.asStateFlow()

    fun queueSharedImage(uri: Uri) {
        incomingImage.value = uri
    }

    fun consumeSharedImage(uri: Uri) {
        if (incomingImage.value == uri) incomingImage.value = null
    }

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
