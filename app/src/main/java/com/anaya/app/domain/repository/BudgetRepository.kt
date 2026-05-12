package com.anaya.app.domain.repository

import com.anaya.app.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getAllBudgets(): Flow<List<Budget>>

    suspend fun getBudgetById(id: Long): Budget?

    suspend fun getActiveBudgetForCategory(categoryId: Long, date: Long): Budget?

    suspend fun insert(budget: Budget): Long

    suspend fun update(budget: Budget)

    suspend fun delete(budget: Budget)
}
