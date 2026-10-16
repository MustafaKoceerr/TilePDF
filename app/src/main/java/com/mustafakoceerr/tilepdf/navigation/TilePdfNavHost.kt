package com.mustafakoceerr.tilepdf.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mustafakoceerr.tilepdf.feature.library.navigation.LibraryRoute
import com.mustafakoceerr.tilepdf.feature.library.navigation.libraryScreen
import com.mustafakoceerr.tilepdf.feature.reader.navigation.navigateToReader
import com.mustafakoceerr.tilepdf.feature.reader.navigation.readerScreen

/**
 * @param openDocument a PDF another app sent, opened in the reader once;
 *   [onDocumentOpened] then clears it.
 */
@Composable
fun TilePdfNavHost(
    openDocument: String?,
    onDocumentOpened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    LaunchedEffect(openDocument) {
        if (openDocument != null) {
            navController.navigateToReader(openDocument)
            onDocumentOpened()
        }
    }
    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = modifier,
    ) {
        libraryScreen(onOpenDocument = navController::navigateToReader)
        readerScreen(onBack = navController::popBackStack)
    }
}
