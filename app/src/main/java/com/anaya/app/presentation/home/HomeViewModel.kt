package com.anaya.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.util.getMonthEndMillis
import com.anaya.app.util.getMonthStartMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val monthBalance: Long = 0,
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val totalBalance: Long = 0,
    val recentTransactions: List<TransactionDisplay> = emptyList(),
    val isLoading: Boolean = true
)

data class TransactionDisplay(
    val id: Long,
    val amount: Long,
    val type: TransactionType,
    val categoryName: String,
    val categoryIcon: String,
    val note: String?,
    val date: Long
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val now = Calendar.getInstance()
    private val monthStart = getMonthStartMillis(
        now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1
    )
    private val monthEnd = getMonthEndMillis(
        now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1
    )

    private val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val accounts: StateFlow<List<Account>> = accountRepository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<HomeUiState> = combine(
        transactionRepository.getTransactionsByDateRange(monthStart, monthEnd),
        transactionRepository.getAllTransactions().map { list -> list.take(5) },
        transactionRepository.getAllTransactions(),
        categories,
        accounts
    ) { monthTx, recentTx, allTx, cats, accts ->
        // 转账不计入收支统计
        val income = monthTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = monthTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        // 总账单
        val totalIncome = allTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = allTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        val recentDisplays = recentTx.map { tx ->
            val category = cats.find { it.id == tx.categoryId }
            val account = accts.find { it.id == tx.accountId }
            val targetAccount = tx.targetAccountId?.let { accts.find { a -> a.id == it } }
            val isTransfer = tx.type == TransactionType.TRANSFER
            TransactionDisplay(
                id = tx.id,
                amount = tx.amount,
                type = tx.type,
                categoryName = if (isTransfer) {
                    targetAccount?.let { "${account?.name ?: "?"} → ${it.name}" }
                        ?: "转账"
                } else {
                    category?.name ?: "未分类"
                },
                categoryIcon = if (isTransfer) "🔄" else (category?.icon ?: "📄"),
                note = tx.note,
                date = tx.date
            )
        }

        HomeUiState(
            monthIncome = income,
            monthExpense = expense,
            monthBalance = income - expense,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            totalBalance = totalIncome - totalExpense,
            recentTransactions = recentDisplays,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteById(id)
        }
    }
}
