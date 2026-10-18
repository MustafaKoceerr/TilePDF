package com.mustafakoceerr.tilepdf.feature.reader.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mustafakoceerr.tilepdf.feature.reader.ReaderScreen
import kotlinx.serialization.Serializable

/** [uri] is a SAF content URI string. */
@Serializable
data class ReaderRoute(val uri: String)

fun NavController.navigateToReader(uri: String) = navigate(ReaderRoute(uri)) {
    launchSingleTop = true
}

/** [onOpenDocument] replaces the reader with another document, e.g. a saved copy. */
fun NavGraphBuilder.readerScreen(onBack: () -> Unit, onOpenDocument: (uri: String) -> Unit) {
    composable<ReaderRoute> {
        ReaderScreen(onBack = onBack, onOpenDocument = onOpenDocument)
    }
}
