package com.ssr.safitsafety.navigation

sealed class Screen(val route: String) {
    object Scan: Screen("scan_screen")
    object Data: Screen("data_screen")
}