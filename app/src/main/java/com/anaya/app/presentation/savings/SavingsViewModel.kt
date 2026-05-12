package com.anaya.app.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.CategoryType
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.ml.LocalModelManager
import com.anaya.app.ml.SavingTip
import com.anaya.app.util.getMonthEndMillis
import com.anaya.app.util.getMonthStartMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class SavingsUiState(
    val tips: List<SavingTip> = emptyList(),
    val totalExpense: Long = 0,
    val totalIncome: Long = 0,
    val selectedMonth: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true,
    val acknowledgedTipIndices: Set<Int> = emptySet(),
    val totalPotentialSavings: Long = 0
)

@HiltViewModel
class SavingsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val localModel: LocalModelManager
) : ViewModel() {

    private val _state = MutableStateFlow(SavingsUiState())
    val uiState: StateFlow<SavingsUiState> = _state.asStateFlow()

    init {
        refreshTips()
    }

    fun refreshTips() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val ym = _state.value.selectedMonth
            val start = getMonthStartMillis(ym.year, ym.monthValue)
            val end = getMonthEndMillis(ym.year, ym.monthValue)

            val txList = transactionRepository.getTransactionsByDateRange(start, end).first()
            val cats = categoryRepository.getAllCategories().first()

            val expenseTxs = txList.filter { it.type == TransactionType.EXPENSE }
            val incomeTxs = txList.filter { it.type == TransactionType.INCOME }

            val totalExpense = expenseTxs.sumOf { it.amount }
            val totalIncome = incomeTxs.sumOf { it.amount }

            // Build category -> amount map using category names
            val categoryMap = expenseTxs.groupBy { tx ->
                cats.find { it.id == tx.categoryId }?.name ?: "未分类"
            }.mapValues { (_, txs) -> txs.sumOf { it.amount } }

            val tips = localModel.generateSavingTips(categoryMap, totalExpense, totalIncome)
            val totalSavings = tips.sumOf { it.estimatedSavings ?: 0L }

            _state.update {
                it.copy(
                    tips = tips,
                    totalExpense = totalExpense,
                    totalIncome = totalIncome,
                    isLoading = false,
                    totalPotentialSavings = totalSavings
                )
            }
        }
    }

    fun previousMonth() {
        _state.update { it.copy(selectedMonth = it.selectedMonth.minusMonths(1)) }
        refreshTips()
    }

    fun nextMonth() {
        _state.update { it.copy(selectedMonth = it.selectedMonth.plusMonths(1)) }
        refreshTips()
    }

    fun acknowledgeTip(index: Int) {
        _state.update {
            it.copy(acknowledgedTipIndices = it.acknowledgedTipIndices + index)
        }
    }

    fun acknowledgeAll() {
        _state.update {
            it.copy(acknowledgedTipIndices = it.tips.indices.toSet())
        }
    }
}
