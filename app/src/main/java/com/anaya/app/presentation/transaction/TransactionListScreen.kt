package com.anaya.app.presentation.transaction

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun TransactionListScreen() {
    Text(
        text = "账单",
        style = MaterialTheme.typography.headlineMedium
    )
}
