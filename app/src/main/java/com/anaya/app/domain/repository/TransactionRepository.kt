package com.anaya.app.domain.repository

import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAllTransactions(): Flow<List<Transaction>>

    suspend fun getTransactionById(id: Long): Transaction?

    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>>

    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>

    suspend fun insert(transaction: Transaction): Long

    suspend fun update(transaction: Transaction)

    suspend fun delete(transaction: Transaction)

    suspend fun deleteById(id: Long)
}
