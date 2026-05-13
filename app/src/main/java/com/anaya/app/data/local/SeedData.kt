package com.anaya.app.data.local

import android.util.Log
import com.anaya.app.data.local.entity.AccountEntity
import com.anaya.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object SeedData {

    val expenseCategories = listOf(
        CategoryEntity(name = "餐饮/正餐", icon = "🍚", type = "EXPENSE", sortOrder = 1),
        CategoryEntity(name = "餐饮/外卖", icon = "🥡", type = "EXPENSE", sortOrder = 2),
        CategoryEntity(name = "餐饮/早餐", icon = "🌅", type = "EXPENSE", sortOrder = 3),
        CategoryEntity(name = "交通/地铁", icon = "🚇", type = "EXPENSE", sortOrder = 4),
        CategoryEntity(name = "交通/打车", icon = "🚕", type = "EXPENSE", sortOrder = 5),
        CategoryEntity(name = "交通/公交", icon = "🚌", type = "EXPENSE", sortOrder = 6),
        CategoryEntity(name = "购物/日用", icon = "🛍️", type = "EXPENSE", sortOrder = 7),
        CategoryEntity(name = "购物/服饰", icon = "👕", type = "EXPENSE", sortOrder = 8),
        CategoryEntity(name = "娱乐/电影", icon = "🎬", type = "EXPENSE", sortOrder = 9),
        CategoryEntity(name = "娱乐/游戏", icon = "🎮", type = "EXPENSE", sortOrder = 10),
        CategoryEntity(name = "住房/房租", icon = "🏠", type = "EXPENSE", sortOrder = 11),
        CategoryEntity(name = "住房/水电", icon = "💡", type = "EXPENSE", sortOrder = 12),
        CategoryEntity(name = "通讯/话费", icon = "📱", type = "EXPENSE", sortOrder = 13),
        CategoryEntity(name = "医疗/看病", icon = "🏥", type = "EXPENSE", sortOrder = 14),
        CategoryEntity(name = "教育/学习", icon = "📚", type = "EXPENSE", sortOrder = 15),
        CategoryEntity(name = "转账", icon = "🔄", type = "EXPENSE", sortOrder = 16),
        CategoryEntity(name = "其他支出", icon = "📦", type = "EXPENSE", sortOrder = 99)
    )

    val incomeCategories = listOf(
        CategoryEntity(name = "工资", icon = "💰", type = "INCOME", sortOrder = 1),
        CategoryEntity(name = "兼职", icon = "💼", type = "INCOME", sortOrder = 2),
        CategoryEntity(name = "投资", icon = "📈", type = "INCOME", sortOrder = 3),
        CategoryEntity(name = "红包", icon = "🧧", type = "INCOME", sortOrder = 4),
        CategoryEntity(name = "退款", icon = "↩️", type = "INCOME", sortOrder = 5),
        CategoryEntity(name = "其他收入", icon = "💵", type = "INCOME", sortOrder = 99)
    )

    val defaultAccounts = listOf(
        AccountEntity(name = "现金", type = "CASH", sortOrder = 1),
        AccountEntity(name = "银行卡", type = "BANK", sortOrder = 2),
        AccountEntity(name = "支付宝", type = "ALIPAY", sortOrder = 3),
        AccountEntity(name = "微信", type = "WECHAT", sortOrder = 4)
    )

    suspend fun seedIfEmpty(database: AppDatabase) {
        val categoryDao = database.categoryDao()
        val accountDao = database.accountDao()

        val existingCategories = categoryDao.getAllCategories().first()
        if (existingCategories.isEmpty()) {
            expenseCategories.forEach { categoryDao.insert(it) }
            incomeCategories.forEach { categoryDao.insert(it) }
            Log.i("SeedData", "Seeded ${expenseCategories.size + incomeCategories.size} categories")
        } else {
            // 仅当真正损坏时修复（所有分类名都为空）
            val allBlank = existingCategories.all { it.name.isBlank() }
            if (allBlank) {
                Log.w("SeedData", "All category names are blank, repairing...")
                categoryDao.deleteAll()
                expenseCategories.forEach { categoryDao.insert(it) }
                incomeCategories.forEach { categoryDao.insert(it) }
            }
        }

        val existingAccounts = accountDao.getAllAccounts().first()
        if (existingAccounts.isEmpty()) {
            defaultAccounts.forEach { accountDao.insert(it) }
            Log.i("SeedData", "Seeded ${defaultAccounts.size} accounts")
        }
    }
}
