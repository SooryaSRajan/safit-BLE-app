package com.ssr.safitsafety.navigation

sealed class Screen(val route: String) {
    object Scan: Screen("scan_screen")
    object Data: Screen("data_screen")
    object Permissions: Screen("permissions_screen")
    object UserData: Screen("user_data_screen")
}