package com.anaya.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.anaya.app.presentation.budget.BudgetScreen
import com.anaya.app.presentation.home.HomeScreen
import com.anaya.app.presentation.settings.AccountManagerScreen
import com.anaya.app.presentation.settings.CategoryManagerScreen
import com.anaya.app.presentation.settings.SettingsScreen
import com.anaya.app.presentation.smartcapture.SmartCaptureScreen
import com.anaya.app.presentation.savings.SavingsScreen
import com.anaya.app.presentation.setup.SetupScreen
import com.anaya.app.presentation.stats.StatsScreen
import com.anaya.app.presentation.transaction.TransactionListScreen
import com.anaya.app.presentation.transaction.editor.TransactionEditorScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onAddTransaction = {
                    navController.navigate("transaction/editor?transactionId=0")
                },
                onTransactionClick = { id ->
                    navController.navigate("transaction/editor?transactionId=$id")
                }
            )
        }

        composable(Screen.Transactions.route) {
            TransactionListScreen(
                onTransactionClick = { id ->
                    navController.navigate("transaction/editor?transactionId=$id")
                }
            )
        }

        composable(
            route = "transaction/editor?transactionId={transactionId}",
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) {
            TransactionEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Stats.route) {
            StatsScreen()
        }

        composable(Screen.Budget.route) {
            BudgetScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToCategories = {
                    navController.navigate(Screen.CategoryManager.route)
                },
                onNavigateToAccounts = {
                    navController.navigate(Screen.AccountManager.route)
                },
                onNavigateToSmartCapture = {
                    navController.navigate(Screen.SmartCapture.route)
                }
            )
        }

        composable(Screen.CategoryManager.route) {
            CategoryManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AccountManager.route) {
            AccountManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SmartCapture.route) {
            SmartCaptureScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Savings.route) {
            SavingsScreen()
        }

        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
