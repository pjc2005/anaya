package com.anaya.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val amount: Long,
    val period: String = "MONTHLY",
    val startDate: Long,
    val endDate: Long,
    val alertThreshold: Int = 80,
    val note: String? = null
)
