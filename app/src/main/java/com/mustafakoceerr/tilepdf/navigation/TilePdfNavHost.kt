package com.mustafakoceerr.tilepdf.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mustafakoceerr.tilepdf.feature.library.navigation.LibraryRoute
import com.mustafakoceerr.tilepdf.feature.library.navigation.libraryScreen
import com.mustafakoceerr.tilepdf.feature.reader.navigation.navigateToReader
import com.mustafakoceerr.tilepdf.feature.reader.navigation.readerScreen

@Composable
fun TilePdfNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = modifier,
    ) {
        libraryScreen(onOpenDocument = navController::navigateToReader)
        readerScreen(onBack = navController::popBackStack)
    }
}
