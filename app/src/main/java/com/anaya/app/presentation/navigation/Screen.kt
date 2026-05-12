package com.anaya.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        label = "首页",
        icon = Icons.Default.Home
    )

    data object Transactions : Screen(
        route = "transactions",
        label = "账单",
        icon = Icons.Default.Receipt
    )

    data object Stats : Screen(
        route = "stats",
        label = "统计",
        icon = Icons.Default.BarChart
    )

    data object Budget : Screen(
        route = "budget",
        label = "预算",
        icon = Icons.Default.AccountBalance
    )

    data object Settings : Screen(
        route = "settings",
        label = "设置",
        icon = Icons.Default.Settings
    )

    companion object {
        val bottomNavItems = listOf(Home, Transactions, Stats, Budget, Settings)
    }
}
