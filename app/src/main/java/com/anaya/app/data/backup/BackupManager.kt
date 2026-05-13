package com.anaya.app.data.backup

import android.content.Context
import android.os.Environment
import com.anaya.app.data.local.dao.AccountDao
import com.anaya.app.data.local.dao.CategoryDao
import com.anaya.app.data.local.dao.TransactionDao
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {

    suspend fun backupToDownloads(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val transactions = transactionDao.getAllTransactionsSync()
            val accounts = accountDao.getAllAccountsSync()
            val categories = categoryDao.getAllCategoriesSync()

            val root = JSONObject().apply {
                put("version", 1)
                put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                put("app", "Anaya")
                put("totalTransactions", transactions.size)

                val acctArr = JSONArray()
                accounts.forEach { acct ->
                    acctArr.put(JSONObject().apply {
                        put("id", acct.id)
                        put("name", acct.name)
                        put("type", acct.type)
                        put("balance", acct.balance)
                        put("initialBalance", acct.initialBalance)
                        put("sortOrder", acct.sortOrder)
                        put("archived", acct.archived)
                    })
                }
                put("accounts", acctArr)

                val catArr = JSONArray()
                categories.forEach { cat ->
                    catArr.put(JSONObject().apply {
                        put("id", cat.id)
                        put("name", cat.name)
                        put("icon", cat.icon ?: JSONObject.NULL)
                        put("type", cat.type)
                        put("parentId", cat.parentId ?: JSONObject.NULL)
                        put("sortOrder", cat.sortOrder)
                    })
                }
                put("categories", catArr)

                val txArr = JSONArray()
                transactions.forEach { tx ->
                    txArr.put(JSONObject().apply {
                        put("amount", tx.amount)
                        put("type", tx.type)
                        put("categoryId", tx.categoryId)
                        put("accountId", tx.accountId)
                        if (tx.targetAccountId != null) put("targetAccountId", tx.targetAccountId)
                        if (tx.note != null) put("note", tx.note)
                        put("date", tx.date)
                        put("createdAt", tx.createdAt)
                        put("updatedAt", tx.updatedAt)
                    })
                }
                put("transactions", txArr)
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val fileName = "anaya_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val file = File(downloadsDir, fileName)
            file.writeText(root.toString(2), Charsets.UTF_8)

            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromUri(uri: android.net.Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("无法打开文件"))

            val text = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            val root = JSONObject(text)

            // Restore accounts first
            val acctArr = root.optJSONArray("accounts")
            if (acctArr != null) {
                for (i in 0 until acctArr.length()) {
                    val obj = acctArr.getJSONObject(i)
                    val existing = accountDao.getAccountById(obj.optLong("id", 0))
                    if (existing == null) {
                        accountRepository.insert(
                            com.anaya.app.domain.model.Account(
                                name = obj.getString("name"),
                                type = try {
                                    com.anaya.app.domain.model.AccountType.valueOf(obj.optString("type", "CASH"))
                                } catch (_: Exception) {
                                    com.anaya.app.domain.model.AccountType.CASH
                                },
                                initialBalance = obj.optLong("initialBalance", 0),
                                balance = obj.optLong("balance", 0),
                                sortOrder = obj.optInt("sortOrder", 0),
                                archived = obj.optBoolean("archived", false)
                            )
                        )
                    }
                }
            }

            // Restore categories
            val catArr = root.optJSONArray("categories")
            if (catArr != null) {
                for (i in 0 until catArr.length()) {
                    val obj = catArr.getJSONObject(i)
                    val existing = categoryDao.getCategoryById(obj.optLong("id", 0))
                    if (existing == null) {
                        categoryRepository.insert(
                            com.anaya.app.domain.model.Category(
                                name = obj.getString("name"),
                                icon = obj.optString("icon", null)?.takeIf { it != "null" },
                                type = try {
                                    com.anaya.app.domain.model.CategoryType.valueOf(obj.optString("type", "EXPENSE"))
                                } catch (_: Exception) {
                                    com.anaya.app.domain.model.CategoryType.EXPENSE
                                },
                                parentId = obj.optLong("parentId", -1).takeIf { it >= 0 },
                                sortOrder = obj.optInt("sortOrder", 0)
                            )
                        )
                    }
                }
            }

            // Restore transactions
            val txArr = root.optJSONArray("transactions")
            var count = 0
            if (txArr != null) {
                for (i in 0 until txArr.length()) {
                    try {
                        val obj = txArr.getJSONObject(i)
                        val tx = Transaction(
                            amount = obj.optLong("amount", 0),
                            type = try {
                                TransactionType.valueOf(obj.optString("type", "EXPENSE"))
                            } catch (_: Exception) {
                                TransactionType.EXPENSE
                            },
                            categoryId = obj.optLong("categoryId", 0),
                            accountId = obj.optLong("accountId", 1),
                            targetAccountId = if (obj.has("targetAccountId") && !obj.isNull("targetAccountId"))
                                obj.getLong("targetAccountId") else null,
                            note = obj.optString("note", null)?.takeIf { it.isNotBlank() && it != "null" },
                            date = obj.optLong("date", System.currentTimeMillis()),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                        if (tx.amount > 0) {
                            transactionRepository.insert(tx)
                            count++
                        }
                    } catch (_: Exception) { }
                }
            }

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
