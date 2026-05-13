package com.anaya.app.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.YearMonth
import java.util.Calendar
import javax.inject.Inject

enum class TimePeriod { MONTH, QUARTER, YEAR, ALL }

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
    val timePeriod: TimePeriod = TimePeriod.MONTH,
    val selectedYearMonth: YearMonth = YearMonth.now(),
    val selectedQuarter: Int = ((YearMonth.now().monthValue - 1) / 3) + 1,
    val selectedYear: Int = YearMonth.now().year,
    val categoryStats: List<CategoryStat> = emptyList(),
    val dailyStats: List<DailyStat> = emptyList(),
    val totalExpense: Long = 0,
    val totalIncome: Long = 0,
    val isLoading: Boolean = true
)

private data class TimeSelection(
    val period: TimePeriod = TimePeriod.MONTH,
    val yearMonth: YearMonth = YearMonth.now(),
    val quarter: Int = ((YearMonth.now().monthValue - 1) / 3) + 1,
    val year: Int = YearMonth.now().year
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _timeSelection = MutableStateFlow(TimeSelection())

    private val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dateFilter = _timeSelection.map { ts ->
        when (ts.period) {
            TimePeriod.MONTH -> {
                val start = getMonthStartMillis(ts.yearMonth.year, ts.yearMonth.monthValue)
                val end = getMonthEndMillis(ts.yearMonth.year, ts.yearMonth.monthValue)
                Pair(start, end)
            }
            TimePeriod.QUARTER -> {
                val start = getQuarterStartMillis(ts.year, ts.quarter)
                val end = getQuarterEndMillis(ts.year, ts.quarter)
                Pair(start, end)
            }
            TimePeriod.YEAR -> {
                val start = getYearStartMillis(ts.year)
                val end = getYearEndMillis(ts.year)
                Pair(start, end)
            }
            TimePeriod.ALL -> {
                Pair(0L, Long.MAX_VALUE)
            }
        }
    }

    private val transactions = dateFilter.flatMapLatest { (start, end) ->
        val ts = _timeSelection.value
        if (ts.period == TimePeriod.ALL) {
            transactionRepository.getAllTransactions()
        } else {
            transactionRepository.getTransactionsByDateRange(start, end)
        }
    }

    val uiState: StateFlow<StatsUiState> = combine(
        transactions, categories, _timeSelection
    ) { txList: List<Transaction>, cats: List<Category>, ts: TimeSelection ->
        val expenseTxs = txList.filter { it.type == TransactionType.EXPENSE }
        val incomeTxs = txList.filter { it.type == TransactionType.INCOME }

        val totalExpense = expenseTxs.sumOf { it.amount }
        val totalIncome = incomeTxs.sumOf { it.amount }

        // Category breakdown (expense only)
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

        // Daily/period breakdown
        val dailyStats = buildDailyStats(expenseTxs, incomeTxs, ts)

        StatsUiState(
            timePeriod = ts.period,
            selectedYearMonth = ts.yearMonth,
            selectedQuarter = ts.quarter,
            selectedYear = ts.year,
            categoryStats = categoryStats,
            dailyStats = dailyStats,
            totalExpense = totalExpense,
            totalIncome = totalIncome,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    private fun buildDailyStats(
        expenseTxs: List<Transaction>,
        incomeTxs: List<Transaction>,
        ts: TimeSelection
    ): List<DailyStat> = when (ts.period) {
        TimePeriod.MONTH -> {
            val expenseMap = expenseTxs.groupBy { tx ->
                Calendar.getInstance().apply { timeInMillis = tx.date }.get(Calendar.DAY_OF_MONTH)
            }
            val incomeMap = incomeTxs.groupBy { tx ->
                Calendar.getInstance().apply { timeInMillis = tx.date }.get(Calendar.DAY_OF_MONTH)
            }
            val daysInMonth = ts.yearMonth.lengthOfMonth()
            (1..daysInMonth).map { day ->
                DailyStat(
                    dayOfMonth = day,
                    expense = expenseMap[day]?.sumOf { it.amount } ?: 0,
                    income = incomeMap[day]?.sumOf { it.amount } ?: 0
                )
            }
        }
        TimePeriod.QUARTER -> {
            val startMonth = (ts.quarter - 1) * 3 + 1
            (startMonth..startMonth + 2).map { month ->
                val mStart = getMonthStartMillis(ts.year, month)
                val mEnd = getMonthEndMillis(ts.year, month)
                DailyStat(
                    dayOfMonth = month,
                    expense = expenseTxs.filter { it.date in mStart..mEnd }.sumOf { it.amount },
                    income = incomeTxs.filter { it.date in mStart..mEnd }.sumOf { it.amount }
                )
            }
        }
        TimePeriod.YEAR, TimePeriod.ALL -> {
            val y = if (ts.period == TimePeriod.YEAR) ts.year else YearMonth.now().year
            (1..12).map { month ->
                val mStart = getMonthStartMillis(y, month)
                val mEnd = getMonthEndMillis(y, month)
                DailyStat(
                    dayOfMonth = month,
                    expense = expenseTxs.filter { it.date in mStart..mEnd }.sumOf { it.amount },
                    income = incomeTxs.filter { it.date in mStart..mEnd }.sumOf { it.amount }
                )
            }
        }
    }

    fun setTimePeriod(period: TimePeriod) {
        _timeSelection.value = _timeSelection.value.copy(period = period)
    }

    fun previousPeriod() {
        val current = _timeSelection.value
        _timeSelection.value = when (current.period) {
            TimePeriod.MONTH -> current.copy(yearMonth = current.yearMonth.minusMonths(1))
            TimePeriod.QUARTER -> {
                if (current.quarter > 1) current.copy(quarter = current.quarter - 1)
                else current.copy(year = current.year - 1, quarter = 4)
            }
            TimePeriod.YEAR -> current.copy(year = current.year - 1)
            TimePeriod.ALL -> current
        }
    }

    fun nextPeriod() {
        val current = _timeSelection.value
        _timeSelection.value = when (current.period) {
            TimePeriod.MONTH -> current.copy(yearMonth = current.yearMonth.plusMonths(1))
            TimePeriod.QUARTER -> {
                if (current.quarter < 4) current.copy(quarter = current.quarter + 1)
                else current.copy(year = current.year + 1, quarter = 1)
            }
            TimePeriod.YEAR -> current.copy(year = current.year + 1)
            TimePeriod.ALL -> current
        }
    }
}

private val colorPalette = listOf(
    0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63,
    0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722, 0xFF795548,
    0xFF607D8B, 0xFFCDDC39
)

fun getStatColor(index: Int): Long = colorPalette[index % colorPalette.size]
