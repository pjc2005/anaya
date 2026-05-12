package com.anaya.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.model.AccountType
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.CategoryType
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategorySettingsState(
    val categories: List<Category> = emptyList(),
    val selectedTab: CategoryType = CategoryType.EXPENSE,
    val showDialog: Boolean = false,
    val editingCategory: Category? = null,
    val dialogName: String = "",
    val dialogType: CategoryType = CategoryType.EXPENSE,
    val dialogIcon: String = "📦"
)

data class AccountSettingsState(
    val accounts: List<Account> = emptyList(),
    val showDialog: Boolean = false,
    val editingAccount: Account? = null,
    val dialogName: String = "",
    val dialogBalance: String = "0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _categoryState = MutableStateFlow(CategorySettingsState())
    val categoryState: StateFlow<CategorySettingsState> = _categoryState.asStateFlow()

    private val _accountState = MutableStateFlow(AccountSettingsState())
    val accountState: StateFlow<AccountSettingsState> = _accountState.asStateFlow()

    private val categoryFlow = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCategories: StateFlow<List<Category>> = combine(
        categoryFlow, _categoryState
    ) { cats, state ->
        cats.filter { it.type == state.selectedTab }.sortedBy { it.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<Account>> = accountRepository.getAllAccounts()
        .map { accounts -> accounts.sortedBy { it.sortOrder } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Sync filtered count into category state
        viewModelScope.launch {
            categoryFlow.collect { cats ->
                _categoryState.update {
                    it.copy(categories = cats)
                }
            }
        }
    }

    // ── Category ──

    fun selectCategoryTab(type: CategoryType) {
        _categoryState.update { it.copy(selectedTab = type) }
    }

    fun showAddCategoryDialog() {
        _categoryState.update {
            it.copy(
                showDialog = true,
                editingCategory = null,
                dialogName = "",
                dialogType = it.selectedTab,
                dialogIcon = "📦"
            )
        }
    }

    fun showEditCategoryDialog(category: Category) {
        _categoryState.update {
            it.copy(
                showDialog = true,
                editingCategory = category,
                dialogName = category.name,
                dialogType = category.type,
                dialogIcon = category.icon ?: "📦"
            )
        }
    }

    fun onCategoryDialogNameChanged(name: String) {
        _categoryState.update { it.copy(dialogName = name) }
    }

    fun onCategoryDialogIconChanged(icon: String) {
        _categoryState.update { it.copy(dialogIcon = icon) }
    }

    fun saveCategory() {
        val state = _categoryState.value
        if (state.dialogName.isBlank()) return
        viewModelScope.launch {
            if (state.editingCategory != null) {
                categoryRepository.update(
                    state.editingCategory.copy(
                        name = state.dialogName,
                        icon = state.dialogIcon
                    )
                )
            } else {
                categoryRepository.insert(
                    Category(
                        name = state.dialogName,
                        icon = state.dialogIcon,
                        type = state.dialogType,
                        sortOrder = (filteredCategories.value.size + 1) * 10
                    )
                )
            }
            _categoryState.update { it.copy(showDialog = false) }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.delete(category)
        }
    }

    fun dismissCategoryDialog() {
        _categoryState.update { it.copy(showDialog = false) }
    }

    // ── Account ──

    fun addAccount(name: String, type: AccountType, initialBalance: Long) {
        viewModelScope.launch {
            accountRepository.insert(
                Account(
                    name = name,
                    type = type,
                    initialBalance = initialBalance,
                    sortOrder = accounts.value.size + 1
                )
            )
        }
    }

    fun showAddAccountDialog() {
        _accountState.update {
            it.copy(showDialog = true, editingAccount = null, dialogName = "", dialogBalance = "0")
        }
    }

    fun showEditAccountDialog(account: Account) {
        _accountState.update {
            it.copy(
                showDialog = true,
                editingAccount = account,
                dialogName = account.name,
                dialogBalance = "0"
            )
        }
    }

    fun onAccountDialogNameChanged(name: String) {
        _accountState.update { it.copy(dialogName = name) }
    }

    fun onAccountDialogBalanceChanged(balance: String) {
        _accountState.update { it.copy(dialogBalance = balance) }
    }

    fun saveAccount() {
        val state = _accountState.value
        if (state.dialogName.isBlank()) return
        viewModelScope.launch {
            if (state.editingAccount != null) {
                accountRepository.update(
                    state.editingAccount.copy(name = state.dialogName)
                )
            } else {
                accountRepository.insert(
                    Account(name = state.dialogName, sortOrder = accounts.value.size + 1)
                )
            }
            _accountState.update { it.copy(showDialog = false) }
        }
    }

    fun toggleAccountArchived(account: Account) {
        viewModelScope.launch {
            accountRepository.update(account.copy(archived = !account.archived))
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            accountRepository.delete(account)
        }
    }

    fun dismissAccountDialog() {
        _accountState.update { it.copy(showDialog = false) }
    }
}
