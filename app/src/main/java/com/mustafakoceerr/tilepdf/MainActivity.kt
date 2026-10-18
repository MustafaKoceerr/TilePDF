package com.mustafakoceerr.tilepdf

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After recreation the reader is already on the restored back stack.
        if (savedInstanceState == null) pendingDocument = intent.incomingDocumentUri()
        setContent {
            TilePDFTheme {
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
