package com.anaya.app.presentation.smartcapture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.ParsedTransaction
import com.anaya.app.ml.RuleBasedClassifier
import com.anaya.app.service.ClipboardMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetectedTransaction(
    val source: String,
    val parsed: ParsedTransaction,
    val suggestedCategoryId: Long? = null,
    val suggestedAmount: Long = 0
)

data class SmartCaptureUiState(
    val detectedList: List<DetectedTransaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isMonitoring: Boolean = false
)

@HiltViewModel
class SmartCaptureViewModel @Inject constructor(
    private val clipboardMonitor: ClipboardMonitor,
    private val localModel: LocalModelInterface,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SmartCaptureUiState())
    val uiState: StateFlow<SmartCaptureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cats = categoryRepository.getAllCategories().first()
            val accts = accountRepository.getAllAccounts().first()
            _state.update { it.copy(categories = cats, accounts = accts) }

            clipboardMonitor.clipboardFlow.collect { parsed ->
                if (parsed != null && parsed.amount != null) {
                    val cat = RuleBasedClassifier.suggestCategoryName(parsed.merchant, parsed.note)
                    val catId = cats.find { it.name == cat }?.id
                    _state.update {
                        it.copy(
                            detectedList = listOf(
                                DetectedTransaction(
                                    "clipboard", parsed, catId,
                                    parsed.amount ?: 0
                                )
                            ) + it.detectedList
                        )
                    }
                }
            }
        }
    }

    fun acceptTransaction(detected: DetectedTransaction, categoryId: Long?, accountId: Long?) {
        viewModelScope.launch {
            val amount = detected.suggestedAmount
            if (amount <= 0) return@launch
            transactionRepository.insert(
                Transaction(
                    amount = amount,
                    type = TransactionType.EXPENSE,
                    categoryId = categoryId ?: detected.suggestedCategoryId ?: 0,
                    accountId = accountId ?: 1,
                    note = detected.parsed.note,
                    date = System.currentTimeMillis()
                )
            )
            _state.update {
                it.copy(detectedList = it.detectedList - detected)
            }
        }
    }

    fun dismissTransaction(detected: DetectedTransaction) {
        _state.update { it.copy(detectedList = it.detectedList - detected) }
    }

    fun clearAll() {
        _state.update { it.copy(detectedList = emptyList()) }
    }
}
