package com.anaya.app.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.Budget
import com.anaya.app.domain.model.BudgetPeriod
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.BudgetRepository
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.util.getMonthEndMillis
import com.anaya.app.util.getMonthStartMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class BudgetWithSpending(
    val budget: Budget,
    val categoryName: String = "总预算",
    val categoryIcon: String = "📊",
    val spent: Long = 0,
    val percentage: Float = 0f
)

data class BudgetUiState(
    val budgets: List<BudgetWithSpending> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val dialogCategoryId: Long? = null,
    val dialogAmount: String = "",
    val dialogPeriod: BudgetPeriod = BudgetPeriod.MONTHLY,
    val dialogThreshold: String = "80",
    val editingBudget: Budget? = null
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val categories = categoryRepository.getCategoriesByType(CategoryType.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val budgets = budgetRepository.getAllBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(budgets, categories) { b, c ->
                val now = YearMonth.now()
                val start = getMonthStartMillis(now.year, now.monthValue)
                val end = getMonthEndMillis(now.year, now.monthValue)

                // Get all transactions for current month to compute spending
                val allTx = mutableMapOf<Long?, Long>()
                transactionRepository.getTransactionsByDateRange(start, end)
                    .collect { txList ->
                        val expenseTxs = txList.filter { it.type == TransactionType.EXPENSE }
                        val totalSpent = expenseTxs.sumOf { it.amount }
                        val byCategory = expenseTxs.groupBy { it.categoryId }
                            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

                        val budgetList = b.map { budget ->
                            val cat = c.find { it.id == budget.categoryId }
                            val spent = if (budget.categoryId == null) totalSpent
                            else byCategory[budget.categoryId] ?: 0L
                            BudgetWithSpending(
                                budget = budget,
                                categoryName = cat?.name ?: "总预算",
                                categoryIcon = cat?.icon ?: if (budget.categoryId == null) "📊" else "📦",
                                spent = spent,
                                percentage = if (budget.amount > 0) spent.toFloat() / budget.amount else 0f
                            )
                        }
                        _state.update {
                            it.copy(budgets = budgetList, categories = c, isLoading = false)
                        }
                    }
            }.collect()
        }
    }

    fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true, dialogCategoryId = null, dialogAmount = "", dialogThreshold = "80", dialogPeriod = BudgetPeriod.MONTHLY, editingBudget = null) }
    }

    fun dismissDialog() {
        _state.update { it.copy(showAddDialog = false, editingBudget = null) }
    }

    fun setDialogCategoryId(id: Long?) {
        _state.update { it.copy(dialogCategoryId = id) }
    }

    fun setDialogAmount(amount: String) {
        _state.update { it.copy(dialogAmount = amount) }
    }

    fun setDialogPeriod(period: BudgetPeriod) {
        _state.update { it.copy(dialogPeriod = period) }
    }

    fun setDialogThreshold(threshold: String) {
        _state.update { it.copy(dialogThreshold = threshold) }
    }

    fun saveBudget() {
        val s = _state.value
        val amountCents = (s.dialogAmount.toDoubleOrNull() ?: return).let { (it * 100).toLong() }
        if (amountCents <= 0) return
        viewModelScope.launch {
            budgetRepository.insert(
                Budget(
                    categoryId = s.dialogCategoryId,
                    amount = amountCents,
                    period = s.dialogPeriod,
                    startDate = getMonthStartMillis(YearMonth.now().year, YearMonth.now().monthValue),
                    alertThreshold = s.dialogThreshold.toIntOrNull() ?: 80
                )
            )
            _state.update { it.copy(showAddDialog = false) }
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepository.delete(budget)
        }
    }
}
