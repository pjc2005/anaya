package com.anaya.app.data.repository

import com.anaya.app.data.local.dao.AccountDao
import com.anaya.app.data.mapper.toDomain
import com.anaya.app.data.mapper.toEntity
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao
) : AccountRepository {

    override fun getAllAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccounts().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAccountById(id: Long): Account? {
        return accountDao.getAccountById(id)?.toDomain()
    }

    override suspend fun insert(account: Account): Long {
        return accountDao.insert(account.toEntity())
    }

    override suspend fun update(account: Account) {
        accountDao.update(account.toEntity())
    }

    override suspend fun delete(account: Account) {
        accountDao.delete(account.toEntity())
    }
}
