package com.mustafakoceerr.tilepdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mustafakoceerr.tilepdf.navigation.TilePdfNavHost
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TilePDFTheme {
                TilePdfNavHost()
            }
        }
    }
}
