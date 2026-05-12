package com.anaya.app.data.mapper

import com.anaya.app.data.local.entity.AccountEntity
import com.anaya.app.data.local.entity.BudgetEntity
import com.anaya.app.data.local.entity.CategoryEntity
import com.anaya.app.data.local.entity.TransactionEntity
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.model.AccountType
import com.anaya.app.domain.model.Budget
import com.anaya.app.domain.model.BudgetPeriod
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.CategoryType
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import java.util.Calendar

// ── Transaction ──

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = amount,
    type = TransactionType.valueOf(type),
    categoryId = categoryId,
    accountId = accountId,
    targetAccountId = targetAccountId,
    note = note,
    date = date,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount,
    type = type.name,
    categoryId = categoryId,
    accountId = accountId,
    targetAccountId = targetAccountId,
    note = note,
    date = date,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ── Account ──

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = try { AccountType.valueOf(type) } catch (e: IllegalArgumentException) { AccountType.CASH },
    initialBalance = initialBalance,
    balance = balance,
    color = color,
    sortOrder = sortOrder,
    archived = archived
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    balance = balance,
    initialBalance = initialBalance,
    icon = null,
    color = color,
    sortOrder = sortOrder,
    archived = archived
)

// ── Category ──

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    icon = icon,
    type = try { CategoryType.valueOf(type) } catch (e: IllegalArgumentException) { CategoryType.EXPENSE },
    parentId = parentId,
    sortOrder = sortOrder
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    type = type.name,
    parentId = parentId,
    sortOrder = sortOrder
)

// ── Budget ──

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = if (categoryId == 0L) null else categoryId,
    amount = amount,
    period = try { BudgetPeriod.valueOf(period) } catch (e: IllegalArgumentException) { BudgetPeriod.MONTHLY },
    startDate = startDate,
    alertThreshold = alertThreshold
)

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    categoryId = categoryId ?: 0,
    amount = amount,
    period = period.name,
    startDate = startDate,
    endDate = calculateEndDate(startDate, period),
    alertThreshold = alertThreshold
)

private fun calculateEndDate(startDate: Long, period: BudgetPeriod): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = startDate }
    when (period) {
        BudgetPeriod.WEEKLY -> calendar.add(Calendar.DAY_OF_YEAR, 7)
        BudgetPeriod.MONTHLY -> calendar.add(Calendar.MONTH, 1)
        BudgetPeriod.YEARLY -> calendar.add(Calendar.YEAR, 1)
    }
    return calendar.timeInMillis
}
