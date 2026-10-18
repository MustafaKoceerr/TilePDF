package com.mustafakoceerr.tilepdf

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mustafakoceerr.thememanager.ThemeState
import com.mustafakoceerr.thememanager.settingsOrDefault
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mustafakoceerr.tilepdf.navigation.TilePdfNavHost
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme
import dagger.hilt.android.AndroidEntryPoint

/** An AppCompatActivity so the in-app language applies on Android 12 and older too. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** A PDF another app sent us, until the nav host has opened it. */
    private var pendingDocument by mutableStateOf<String?>(null)

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Reading the stored theme takes a few milliseconds; drawing before it
        // would flash the system's theme first.
        splashScreen.setKeepOnScreenCondition { viewModel.themeState.value is ThemeState.Loading }
        enableEdgeToEdge()
        // After recreation the reader is already on the restored back stack.
        if (savedInstanceState == null) pendingDocument = intent.incomingDocumentUri()
        setContent {
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            TilePDFTheme(themeState.settingsOrDefault) {
                TilePdfNavHost(
                    openDocument = pendingDocument,
                    onDocumentOpened = { pendingDocument = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.incomingDocumentUri()?.let { pendingDocument = it }
    }
}
