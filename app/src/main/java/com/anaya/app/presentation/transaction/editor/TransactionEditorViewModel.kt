package com.anaya.app.presentation.transaction.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.CategoryType
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.util.CurrencyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SaveState { IDLE, LOADING, ERROR }

enum class AccountPickerMode { SOURCE, TARGET }

data class EditorUiState(
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val amountDisplay: String = "0",
    val selectedCategory: Category? = null,
    val selectedAccount: Account? = null,
    val targetAccount: Account? = null,
    val note: String = "",
    val selectedDate: Long = System.currentTimeMillis(),
    val isEditing: Boolean = false,
    val saveState: SaveState = SaveState.IDLE,
    val errorMessage: String? = null,
    val accountPickerMode: AccountPickerMode = AccountPickerMode.SOURCE
)

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val transactionId: Long? = savedStateHandle.get<Long>("transactionId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val allAccounts: StateFlow<List<Account>> = accountRepository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccounts: StateFlow<List<Account>> = allAccounts
        .map { accounts -> accounts.filter { !it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allCategories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCategories: StateFlow<List<Category>> = combine(
        allCategories, _uiState
    ) { cats, state ->
        when (state.transactionType) {
            TransactionType.EXPENSE -> cats.filter { it.type == CategoryType.EXPENSE }
            TransactionType.INCOME -> cats.filter { it.type == CategoryType.INCOME }
            TransactionType.TRANSFER -> emptyList() // 转账不需要分类
        }.sortedBy { it.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (transactionId != null) {
            loadTransaction(transactionId)
        }
        viewModelScope.launch {
            val accounts = allAccounts.first()
            if (_uiState.value.selectedAccount == null && accounts.isNotEmpty()) {
                _uiState.update { it.copy(selectedAccount = accounts.first()) }
            }
        }
    }

    private fun loadTransaction(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(saveState = SaveState.LOADING) }
            try {
                val tx = transactionRepository.getTransactionById(id) ?: return@launch
                val account = accountRepository.getAccountById(tx.accountId)
                val category = categoryRepository.getCategoryById(tx.categoryId)
                val targetAccount = tx.targetAccountId?.let { accountRepository.getAccountById(it) }
                _uiState.update {
                    it.copy(
                        transactionType = tx.type,
                        amountDisplay = CurrencyUtils.centsToDisplayString(tx.amount),
                        selectedCategory = category,
                        selectedAccount = account,
                        targetAccount = targetAccount,
                        note = tx.note ?: "",
                        selectedDate = tx.date,
                        isEditing = true,
                        saveState = SaveState.IDLE
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(saveState = SaveState.ERROR, errorMessage = e.message)
                }
            }
        }
    }

    fun onDigitTap(digit: Int) {
        val current = _uiState.value.amountDisplay
        if (current.length >= 12) return
        _uiState.update {
            it.copy(
                amountDisplay = when {
                    current == "0" -> digit.toString()
                    current.contains(".") -> {
                        val parts = current.split(".")
                        if (parts[1].length < 2) "$current$digit" else current
                    }
                    else -> "$current$digit"
                }
            )
        }
    }

    fun onDecimalTap() {
        val current = _uiState.value.amountDisplay
        if (!current.contains(".")) {
            _uiState.update { it.copy(amountDisplay = "$current.") }
        }
    }

    fun onDeleteTap() {
        val current = _uiState.value.amountDisplay
        _uiState.update {
            it.copy(
                amountDisplay = when {
                    current.length <= 1 -> "0"
                    else -> current.dropLast(1)
                }
            )
        }
    }

    fun onTypeSelected(type: TransactionType) {
        _uiState.update {
            it.copy(transactionType = type, selectedCategory = null, targetAccount = null)
        }
    }

    fun onCategorySelected(category: Category) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onAccountSelected(account: Account) {
        val mode = _uiState.value.accountPickerMode
        _uiState.update {
            when (mode) {
                AccountPickerMode.SOURCE -> it.copy(selectedAccount = account)
                AccountPickerMode.TARGET -> it.copy(targetAccount = account)
            }
        }
    }

    fun onTargetAccountSelected(account: Account) {
        _uiState.update { it.copy(targetAccount = account) }
    }

    fun setAccountPickerMode(mode: AccountPickerMode) {
        _uiState.update { it.copy(accountPickerMode = mode) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onDateSelected(date: Long) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun save() {
        val state = _uiState.value
        val amountCents = CurrencyUtils.displayStringToCents(state.amountDisplay)

        if (amountCents <= 0 || state.amountDisplay.endsWith(".")) {
            _uiState.update {
                it.copy(saveState = SaveState.ERROR, errorMessage = "请输入有效金额")
            }
            return
        }
        if (state.selectedAccount == null) {
            _uiState.update {
                it.copy(saveState = SaveState.ERROR, errorMessage = "请选择账户")
            }
            return
        }
        if (state.transactionType == TransactionType.TRANSFER && state.targetAccount == null) {
            _uiState.update {
                it.copy(saveState = SaveState.ERROR, errorMessage = "请选择转入账户")
            }
            return
        }
        if (state.transactionType == TransactionType.TRANSFER
            && state.selectedAccount?.id == state.targetAccount?.id) {
            _uiState.update {
                it.copy(saveState = SaveState.ERROR, errorMessage = "转出和转入账户不能相同")
            }
            return
        }
        // 转账不需要分类，非转账需要分类
        if (state.transactionType != TransactionType.TRANSFER && state.selectedCategory == null) {
            _uiState.update {
                it.copy(saveState = SaveState.ERROR, errorMessage = "请选择分类")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saveState = SaveState.LOADING) }
            try {
                val transaction = Transaction(
                    id = if (state.isEditing) transactionId ?: 0 else 0,
                    amount = amountCents,
                    type = state.transactionType,
                    categoryId = state.selectedCategory?.id ?: 0,
                    accountId = state.selectedAccount!!.id,
                    targetAccountId = if (state.transactionType == TransactionType.TRANSFER) {
                        state.targetAccount?.id
                    } else null,
                    note = state.note.ifBlank { null },
                    date = state.selectedDate,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                if (state.isEditing) {
                    transactionRepository.update(transaction)
                } else {
                    transactionRepository.insert(transaction)
                }
                _uiState.update { it.copy(saveState = SaveState.IDLE) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(saveState = SaveState.ERROR, errorMessage = e.message)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, saveState = SaveState.IDLE) }
    }

    fun deleteTransaction() {
        val id = transactionId ?: return
        viewModelScope.launch {
            try {
                transactionRepository.deleteById(id)
                _uiState.update { it.copy(saveState = SaveState.IDLE) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }
}
