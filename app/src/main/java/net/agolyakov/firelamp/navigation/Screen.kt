package net.agolyakov.firelamp.navigation

sealed class Screen(val route: String) {
    object Home    : Screen(route = "home_screen")
    object Device  : Screen(route = "device_screen")
    object Settings: Screen(route = "settings_screen")
    object Firmware: Screen(route = "firmware_screen/{mode}") {
        const val ARG_MODE = "mode"
        fun path(mode: String) = "firmware_screen/$mode"
    }
    object Demo    : Screen(route = "demo_screen/{version}") {
        const val ARG_VERSION = "version"
        fun path(version: String) = "demo_screen/$version"
    }
}
