package com.anaya.app.data.repository

import com.anaya.app.data.local.dao.AccountDao
import com.anaya.app.data.local.dao.TransactionDao
import com.anaya.app.data.mapper.toDomain
import com.anaya.app.data.mapper.toEntity
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao
) : TransactionRepository {

    override fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        return transactionDao.getTransactionById(id)?.toDomain()
    }

    override fun getTransactionsByDateRange(
        startDate: Long,
        endDate: Long
    ): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByDateRange(startDate, endDate).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByType(type.name).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun insert(transaction: Transaction): Long {
        val id = transactionDao.insert(transaction.toEntity())
        // Update account balances
        updateBalancesForInsert(transaction)
        return id
    }

    override suspend fun update(transaction: Transaction) {
        // Revert old balances, then apply new ones
        val oldTx = transactionDao.getTransactionById(transaction.id)?.toDomain()
        if (oldTx != null) {
            val newTx = transaction.copy(id = oldTx.id)
            revertBalancesForDelete(oldTx)
            transactionDao.update(newTx.toEntity())
            updateBalancesForInsert(newTx)
        } else {
            transactionDao.update(transaction.toEntity())
            updateBalancesForInsert(transaction)
        }
    }

    override suspend fun delete(transaction: Transaction) {
        revertBalancesForDelete(transaction)
        transactionDao.delete(transaction.toEntity())
    }

    override suspend fun deleteById(id: Long) {
        val tx = transactionDao.getTransactionById(id)?.toDomain() ?: return
        revertBalancesForDelete(tx)
        transactionDao.deleteById(id)
    }

    override suspend fun deleteAll() {
        // Reset all account balances to initialBalance
        val accounts = accountDao.getAllAccountsSync()
        accounts.forEach { account ->
            accountDao.updateBalance(account.id, account.initialBalance)
        }
        // Delete all transactions
        transactionDao.deleteAll()
    }

    private suspend fun updateBalancesForInsert(tx: Transaction) {
        when (tx.type) {
            TransactionType.INCOME -> {
                val account = accountDao.getAccountById(tx.accountId) ?: return
                accountDao.updateBalance(tx.accountId, account.balance + tx.amount)
            }
            TransactionType.EXPENSE -> {
                val account = accountDao.getAccountById(tx.accountId) ?: return
                accountDao.updateBalance(tx.accountId, account.balance - tx.amount)
            }
            TransactionType.TRANSFER -> {
                // Source: decrease
                val source = accountDao.getAccountById(tx.accountId) ?: return
                accountDao.updateBalance(tx.accountId, source.balance - tx.amount)
                // Target: increase
                val targetId = tx.targetAccountId ?: return
                val target = accountDao.getAccountById(targetId) ?: return
                accountDao.updateBalance(targetId, target.balance + tx.amount)
            }
        }
    }

    private suspend fun revertBalancesForDelete(tx: Transaction) {
        when (tx.type) {
            TransactionType.INCOME -> {
                val account = accountDao.getAccountById(tx.accountId) ?: return
                accountDao.updateBalance(tx.accountId, account.balance - tx.amount)
            }
            TransactionType.EXPENSE -> {
                val account = accountDao.getAccountById(tx.accountId) ?: return
                accountDao.updateBalance(tx.accountId, account.balance + tx.amount)
            }
            TransactionType.TRANSFER -> {
                // Source: increase back
                val source = accountDao.getAccountById(tx.accountId) ?: return
                accountDao.updateBalance(tx.accountId, source.balance + tx.amount)
                // Target: decrease back
                val targetId = tx.targetAccountId ?: return
                val target = accountDao.getAccountById(targetId) ?: return
                accountDao.updateBalance(targetId, target.balance - tx.amount)
            }
        }
    }
}
