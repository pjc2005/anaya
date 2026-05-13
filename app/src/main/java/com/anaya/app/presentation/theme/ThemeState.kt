package com.anaya.app.presentation.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局主题状态，跨 Activity/ViewModel 可观察。
 * 设置页改变主题后，MainActivity 能即时响应。
 */
object ThemeState {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun init(prefs: ThemePreferencesManager) {
        _themeMode.value = prefs.getThemeMode()
    }

    fun set(mode: ThemeMode) {
        _themeMode.value = mode
    }
}
