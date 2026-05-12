package com.anaya.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.anaya.app.presentation.budget.BudgetScreen
import com.anaya.app.presentation.home.HomeScreen
import com.anaya.app.presentation.settings.SettingsScreen
import com.anaya.app.presentation.stats.StatsScreen
import com.anaya.app.presentation.transaction.TransactionListScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.Transactions.route) {
            TransactionListScreen()
        }
        composable(Screen.Stats.route) {
            StatsScreen()
        }
        composable(Screen.Budget.route) {
            BudgetScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
