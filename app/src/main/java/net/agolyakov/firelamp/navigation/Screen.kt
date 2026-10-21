package net.agolyakov.firelamp.navigation

sealed class Screen(val route: String) {
    object Home    : Screen(route = "home_screen")
    object Device : Screen(route = "device_screen")
}
