package com.wardrobe.atelier

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.wardrobe.atelier.presentation.WardrobeViewModel
import com.wardrobe.atelier.ui.WardrobeApp

class MainActivity : ComponentActivity() {
    private val wardrobeModel: WardrobeViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(WardrobeViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return WardrobeViewModel(com.wardrobe.atelier.data.LocalWardrobeRepository(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.wardrobe.atelier.presentation.LanguagePreferences.initialize(
            com.wardrobe.atelier.data.LocalLanguageSettings(applicationContext)
        )
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
