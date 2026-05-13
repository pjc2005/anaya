package com.anaya.app.presentation.settings

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.data.backup.BackupManager
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.model.AccountType
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.CategoryType
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.ml.LocalModelManager
import com.anaya.app.ml.ModelStatus
import com.anaya.app.presentation.theme.ThemeMode
import com.anaya.app.presentation.theme.ThemePreferencesManager
import com.anaya.app.presentation.theme.ThemeState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val localModel: LocalModelManager,
    private val themePrefs: ThemePreferencesManager,
    private val backupManager: BackupManager,
    @ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    val modelStatus: StateFlow<ModelStatus> = localModel.modelStatus
    val downloadProgress: StateFlow<Int> = localModel.downloadProgress

    // Theme
    private val _themeMode = MutableStateFlow(themePrefs.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Backup/Restore state
    private val _backupResult = MutableSharedFlow<String>()
    val backupResult: SharedFlow<String> = _backupResult.asSharedFlow()

    private val _isDeletingAll = MutableStateFlow(false)
    val isDeletingAll: StateFlow<Boolean> = _isDeletingAll.asStateFlow()

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

    // ── Theme ──

    fun cycleTheme() {
        val next = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        _themeMode.value = next
        ThemeState.set(next)
        themePrefs.setThemeMode(next)
    }

    // ── Backup/Restore ──

    fun backupData() {
        viewModelScope.launch {
            _backupResult.emit("backup_started")
            val result = backupManager.backupToDownloads()
            if (result.isSuccess) {
                _backupResult.emit("backup_success:${result.getOrNull()}")
            } else {
                _backupResult.emit("backup_error:${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun restoreData(uri: Uri) {
        viewModelScope.launch {
            _backupResult.emit("restore_started")
            val result = backupManager.restoreFromUri(uri)
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _backupResult.emit("restore_success:$count")
            } else {
                _backupResult.emit("restore_error:${result.exceptionOrNull()?.message}")
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
                    balance = initialBalance,
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

    fun checkModelStatus() {
        localModel.checkModelStatus()
    }

    fun downloadModel() {
        viewModelScope.launch {
            localModel.downloadModel()
        }
    }

    // ── Delete All ──

    fun deleteAllTransactions() {
        viewModelScope.launch {
            _isDeletingAll.value = true
            transactionRepository.deleteAll()
            _isDeletingAll.value = false
        }
    }
}
