package com.anaya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anaya.app.di.SetupPrefsManager
import com.anaya.app.presentation.navigation.NavGraph
import com.anaya.app.presentation.navigation.Screen
import com.anaya.app.presentation.theme.AnayaTheme
import com.anaya.app.presentation.theme.ThemeMode
import com.anaya.app.presentation.theme.ThemePreferencesManager
import com.anaya.app.presentation.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var setupPrefs: SetupPrefsManager
    @Inject lateinit var themePrefs: ThemePreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by ThemeState.themeMode.collectAsState()

            // Init theme from preferences
            LaunchedEffect(Unit) {
                ThemeState.init(themePrefs)
            }

            AnayaTheme(themeMode = themeMode) {
                var isSetupComplete by remember { mutableStateOf<Boolean?>(null) }

                // 异步检查设置状态
                LaunchedEffect(Unit) {
                    isSetupComplete = setupPrefs.isSetupComplete()
                }

                when (isSetupComplete) {
                    null -> {
                        // 加载中，可显示闪屏
                    }
                    false -> {
                        // 设置未完成 → 进入设置向导，完成后回调通知主界面
                        SetupNavHost(onSetupComplete = { isSetupComplete = true })
                    }
                    true -> {
                        // 正常主界面
                        AnayaMainScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupNavHost(onSetupComplete: () -> Unit) {
    val navController = rememberNavController()
    NavGraph(
        navController = navController,
        startDestination = Screen.Setup.route,
        modifier = Modifier.fillMaxSize(),
        onSetupComplete = onSetupComplete
    )
}

@Composable
fun AnayaMainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Screens that should show the bottom navigation bar
    val bottomNavRoutes = Screen.bottomNavItems.map { it.route }

    val showBottomBar = currentDestination?.route in bottomNavRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                screen.icon?.let {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = screen.label
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
