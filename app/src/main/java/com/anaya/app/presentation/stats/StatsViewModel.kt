package com.anaya.app.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.util.getMonthEndMillis
import com.anaya.app.util.getMonthStartMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.Calendar
import javax.inject.Inject

data class CategoryStat(
    val categoryName: String,
    val categoryIcon: String,
    val amount: Long,
    val percentage: Float,
    val colorIndex: Int
)

data class DailyStat(
    val dayOfMonth: Int,
    val expense: Long,
    val income: Long
)

data class StatsUiState(
    val categoryStats: List<CategoryStat> = emptyList(),
    val dailyStats: List<DailyStat> = emptyList(),
    val totalExpense: Long = 0,
    val totalIncome: Long = 0,
    val selectedMonth: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())

    private val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val transactions = _selectedYearMonth.flatMapLatest { ym ->
        val start = getMonthStartMillis(ym.year, ym.monthValue)
        val end = getMonthEndMillis(ym.year, ym.monthValue)
        transactionRepository.getTransactionsByDateRange(start, end)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        transactions, categories, _selectedYearMonth
    ) { txList, cats, ym ->
        val expenseTxs = txList.filter { it.type == TransactionType.EXPENSE }
        val incomeTxs = txList.filter { it.type == TransactionType.INCOME }

        // Category breakdown (expense only)
        val totalExpense = expenseTxs.sumOf { it.amount }
        val categoryMap = expenseTxs.groupBy { it.categoryId }
        val categoryStats = categoryMap.map { (catId, txs) ->
            val cat = cats.find { it.id == catId }
            val amount = txs.sumOf { it.amount }
            CategoryStat(
                categoryName = cat?.name ?: "未分类",
                categoryIcon = cat?.icon ?: "📦",
                amount = amount,
                percentage = if (totalExpense > 0) amount.toFloat() / totalExpense else 0f,
                colorIndex = (catId % 10).toInt()
            )
        }.sortedByDescending { it.amount }

        // Daily breakdown
        val dailyMap = expenseTxs.groupBy { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
            cal.get(Calendar.DAY_OF_MONTH)
        }
        val incomeDailyMap = incomeTxs.groupBy { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
            cal.get(Calendar.DAY_OF_MONTH)
        }
        val daysInMonth = ym.lengthOfMonth()
        val dailyStats = (1..daysInMonth).map { day ->
            DailyStat(
                dayOfMonth = day,
                expense = dailyMap[day]?.sumOf { it.amount } ?: 0,
                income = incomeDailyMap[day]?.sumOf { it.amount } ?: 0
            )
        }

        StatsUiState(
            categoryStats = categoryStats,
            dailyStats = dailyStats,
            totalExpense = totalExpense,
            totalIncome = incomeTxs.sumOf { it.amount },
            selectedMonth = ym,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun previousMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.plusMonths(1)
    }
}

private val colorPalette = listOf(
    0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63,
    0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722, 0xFF795548,
    0xFF607D8B, 0xFFCDDC39
)

fun getStatColor(index: Int): Long = colorPalette[index % colorPalette.size]
