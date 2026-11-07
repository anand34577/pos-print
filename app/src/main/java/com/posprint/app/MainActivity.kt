package com.posprint.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.data.Shared
import com.posprint.app.data.settings.DirectionMode
import com.posprint.app.data.settings.ThemeMode
import com.posprint.app.ui.AppRoot
import com.posprint.app.ui.MainViewModel
import com.posprint.app.ui.theme.PosPrintTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val dark = when (ui.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val system = LocalLayoutDirection.current
            val direction = when (ui.direction) {
                DirectionMode.AUTO -> system
                DirectionMode.LTR -> LayoutDirection.Ltr
                DirectionMode.RTL -> LayoutDirection.Rtl
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                PosPrintTheme(darkTheme = dark, dynamicColor = ui.dynamicColor) { AppRoot() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Android Share sheet: text, images and PDFs sent to this app. */
    private fun handle(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        val shared = when {
            type.startsWith("text/") && intent.hasExtra(Intent.EXTRA_TEXT) ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Shared.Text(it) }
            else -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { Shared.File(it.toString(), type) }
        }
        if (shared != null) vm.share.pending.value = shared
    }
}
