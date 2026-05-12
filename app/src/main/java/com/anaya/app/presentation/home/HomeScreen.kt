package com.anaya.app.presentation.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun HomeScreen() {
    Text(
        text = "首页",
        style = MaterialTheme.typography.headlineMedium
    )
}
