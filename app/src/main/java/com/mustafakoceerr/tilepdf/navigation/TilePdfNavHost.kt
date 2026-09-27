package com.mustafakoceerr.tilepdf.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mustafakoceerr.tilepdf.feature.home.HomeScreen
import com.mustafakoceerr.tilepdf.feature.reader.ReaderScreen

@Composable
fun TilePdfNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(onPdfSelected = { uri -> navController.navigate(ReaderRoute(uri)) })
        }
        composable<ReaderRoute> {
            ReaderScreen(onBack = navController::popBackStack)
        }
    }
}
