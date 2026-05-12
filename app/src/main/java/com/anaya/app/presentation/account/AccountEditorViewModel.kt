package com.anaya.app.presentation.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.model.AccountType
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.util.CurrencyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountEditorUiState(
    val name: String = "",
    val type: AccountType = AccountType.CASH,
    val balanceDisplay: String = "0",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
    val accountId: Long? = null
)

@HiltViewModel
class AccountEditorViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val accountId: Long? = savedStateHandle.get<Long>("accountId")?.takeIf { it > 0 }

    private val _state = MutableStateFlow(AccountEditorUiState(accountId = accountId))
    val state: StateFlow<AccountEditorUiState> = _state.asStateFlow()

    init {
        if (accountId != null) {
            loadAccount(accountId)
        }
    }

    private fun loadAccount(id: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val account = accountRepository.getAccountById(id)
            if (account != null) {
                _state.update {
                    it.copy(
                        name = account.name,
                        type = account.type,
                        balanceDisplay = CurrencyUtils.centsToDisplayString(account.initialBalance),
                        isLoading = false
                    )
                }
            } else {
                _state.update { it.copy(isLoading = false, errorMessage = "账户不存在") }
            }
        }
    }

    fun onNameChanged(name: String) {
        _state.update { it.copy(name = name) }
    }

    fun onTypeSelected(type: AccountType) {
        _state.update { it.copy(type = type) }
    }

    fun onBalanceChanged(value: String) {
        if (value.all { c -> c.isDigit() || c == '.' }) {
            _state.update { it.copy(balanceDisplay = value) }
        }
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(errorMessage = "请输入账户名称") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val balanceCents = CurrencyUtils.displayStringToCents(s.balanceDisplay)
                if (accountId != null) {
                    accountRepository.update(
                        Account(
                            id = accountId,
                            name = s.name,
                            type = s.type,
                            initialBalance = balanceCents
                        )
                    )
                }
                _state.update { it.copy(isSaving = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun deleteAccount() {
        val id = accountId ?: return
        viewModelScope.launch {
            try {
                val account = accountRepository.getAccountById(id)
                if (account != null) {
                    accountRepository.delete(account)
                    _state.update { it.copy(isDeleted = true) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
