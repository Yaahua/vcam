package com.yaahua.vcam.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.yaahua.vcam.R

sealed class Screen(val route: String, @StringRes val titleResId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
}