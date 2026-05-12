package com.anaya.app.presentation.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.presentation.home.TransactionDisplay
import com.anaya.app.util.formatDateGroupHeader
import com.anaya.app.util.getMonthEndMillis
import com.anaya.app.util.getMonthStartMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.Calendar
import javax.inject.Inject

data class TransactionListUiState(
    val groupedTransactions: List<DateGroup> = emptyList(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true
)

data class DateGroup(
    val header: String,
    val transactions: List<TransactionDisplay>
)

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth.asStateFlow()

    private val transactions = _selectedYearMonth.flatMapLatest { ym ->
        val start = getMonthStartMillis(ym.year, ym.monthValue)
        val end = getMonthEndMillis(ym.year, ym.monthValue)
        transactionRepository.getTransactionsByDateRange(start, end)
    }

    private val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<TransactionListUiState> = combine(
        transactions, categories, _selectedYearMonth
    ) { txList, cats, ym ->
        val rawGroups = txList.groupBy { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
            cal.get(Calendar.YEAR) * 10000 +
                    (cal.get(Calendar.MONTH) + 1) * 100 +
                    cal.get(Calendar.DAY_OF_MONTH)
        }

        val grouped = rawGroups.entries
            .sortedByDescending { it.key }
            .map { (_, txs) ->
                val headerDate = txs.first().date
                DateGroup(
                    header = formatDateGroupHeader(headerDate),
                    transactions = txs.map { tx ->
                        val category = cats.find { it.id == tx.categoryId }
                        TransactionDisplay(
                            id = tx.id,
                            amount = tx.amount,
                            type = tx.type,
                            categoryName = category?.name ?: "未分类",
                            categoryIcon = category?.icon ?: "📄",
                            note = tx.note,
                            date = tx.date
                        )
                    }
                )
            }

        TransactionListUiState(
            groupedTransactions = grouped,
            selectedMonth = ym,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionListUiState())

    fun previousMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.plusMonths(1)
    }

    fun selectMonth(yearMonth: YearMonth) {
        _selectedYearMonth.value = yearMonth
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteById(id)
        }
    }
}
