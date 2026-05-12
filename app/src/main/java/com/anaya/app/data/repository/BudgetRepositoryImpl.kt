package com.anaya.app.data.repository

import com.anaya.app.data.local.dao.BudgetDao
import com.anaya.app.data.mapper.toDomain
import com.anaya.app.data.mapper.toEntity
import com.anaya.app.domain.model.Budget
import com.anaya.app.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao
) : BudgetRepository {

    override fun getAllBudgets(): Flow<List<Budget>> {
        return budgetDao.getAllBudgets().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getBudgetById(id: Long): Budget? {
        return budgetDao.getBudgetById(id)?.toDomain()
    }

    override suspend fun getActiveBudgetForCategory(
        categoryId: Long,
        date: Long
    ): Budget? {
        return budgetDao.getActiveBudgetForCategory(categoryId, date)?.toDomain()
    }

    override suspend fun insert(budget: Budget): Long {
        return budgetDao.insert(budget.toEntity())
    }

    override suspend fun update(budget: Budget) {
        budgetDao.update(budget.toEntity())
    }

    override suspend fun delete(budget: Budget) {
        budgetDao.delete(budget.toEntity())
    }
}
