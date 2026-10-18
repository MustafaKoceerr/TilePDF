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

fun NavGraphBuilder.readerScreen(onBack: () -> Unit) {
    composable<ReaderRoute> {
        ReaderScreen(onBack = onBack)
    }
}
