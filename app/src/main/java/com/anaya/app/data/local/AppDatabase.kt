package com.anaya.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.anaya.app.data.local.dao.AccountDao
import com.anaya.app.data.local.dao.BudgetDao
import com.anaya.app.data.local.dao.CategoryDao
import com.anaya.app.data.local.dao.TransactionDao
import com.anaya.app.data.local.entity.AccountEntity
import com.anaya.app.data.local.entity.BudgetEntity
import com.anaya.app.data.local.entity.CategoryEntity
import com.anaya.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
}
