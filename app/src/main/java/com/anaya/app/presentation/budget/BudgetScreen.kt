package com.anaya.app.presentation.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun BudgetScreen() {
    Text(
        text = "预算",
        style = MaterialTheme.typography.headlineMedium
    )
}
