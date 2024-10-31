package com.ssr.safitsafety.navigation// com.ssr.safitsafety.navigation.NavigationStack.kt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssr.safitsafety.navigation.pages.DataScreen
import com.ssr.safitsafety.navigation.pages.MainScreen

@Composable
fun NavigationStack(onLaunch: () -> Unit) {

    LaunchedEffect(Unit) {
        onLaunch()
    }

    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Scan.route) {
        composable(route = Screen.Scan.route) {
            MainScreen(navController = navController)
        }
        composable(
            route = Screen.Scan.route + "?text={text}",
            arguments = listOf(
                navArgument("text") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            DataScreen()
        }
    }
}