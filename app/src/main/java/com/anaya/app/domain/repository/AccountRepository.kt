package com.anaya.app.domain.repository

import com.anaya.app.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAllAccounts(): Flow<List<Account>>

    suspend fun getAccountById(id: Long): Account?

    suspend fun insert(account: Account): Long

    suspend fun update(account: Account)

    suspend fun delete(account: Account)

    suspend fun updateBalance(id: Long, newBalance: Long)
}
