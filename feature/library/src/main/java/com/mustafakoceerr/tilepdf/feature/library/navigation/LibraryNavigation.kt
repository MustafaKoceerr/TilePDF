package com.mustafakoceerr.tilepdf.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mustafakoceerr.tilepdf.feature.library.LibraryScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

fun NavGraphBuilder.libraryScreen(onOpenDocument: (uri: String) -> Unit, onOpenSettings: () -> Unit) {
    composable<LibraryRoute> {
        LibraryScreen(onOpenDocument = onOpenDocument, onOpenSettings = onOpenSettings)
    }
}
