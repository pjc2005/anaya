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
import com.anaya.app.service.PaymentEventBus
import com.anaya.app.util.CaptureLogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetectedTransaction(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: String,
    val layer: Int = 1,
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
    private val accountRepository: AccountRepository,
    private val eventBus: PaymentEventBus
) : ViewModel() {

    private val _state = MutableStateFlow(SmartCaptureUiState())
    val uiState: StateFlow<SmartCaptureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cats = categoryRepository.getAllCategories().first()
            val accts = accountRepository.getAllAccounts().first()
            _state.update { it.copy(categories = cats, accounts = accts) }

            // 监听无障碍服务的检测事件
            eventBus.events.collect { parsed ->
                if (parsed != null && parsed.amount != null) {
                    onDetected("accessibility", 1, parsed, cats)
                }
            }
        }

        viewModelScope.launch {
            clipboardMonitor.clipboardFlow.collect { parsed ->
                if (parsed != null && parsed.amount != null) {
                    onDetected("clipboard", 0, parsed, _state.value.categories)
                }
            }
        }

        // 定期从 CaptureLogManager 同步未处理的日志
        viewModelScope.launch {
            while (true) {
                syncFromLogManager()
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    /**
     * 从 CaptureLogManager 同步 PENDING 状态的检测记录
     */
    private fun syncFromLogManager() {
        val existingIds = _state.value.detectedList.map { it.id }.toSet()
        val newEntries = CaptureLogManager.recentLogs
            .filter { it.status == CaptureLogManager.Status.PENDING }
            .filter { it.id !in existingIds }
            .mapNotNull { log ->
                val cats = _state.value.categories
                val parsed = ParsedTransaction(
                    amount = log.amount,
                    merchant = log.merchant,
                    note = log.note ?: log.merchant ?: log.platform,
                    confidence = log.confidence
                )
                val catName = RuleBasedClassifier.suggestCategoryName(log.merchant, log.note)
                DetectedTransaction(
                    id = log.id,
                    timestamp = log.timestamp,
                    source = log.source,
                    layer = log.layer,
                    parsed = parsed,
                    suggestedCategoryId = cats.find { it.name == catName }?.id,
                    suggestedAmount = log.amount
                )
            }
        if (newEntries.isNotEmpty()) {
            _state.update { it.copy(detectedList = it.detectedList + newEntries) }
        }
    }

    private fun onDetected(source: String, layer: Int, parsed: ParsedTransaction, cats: List<Category>) {
        val cat = RuleBasedClassifier.suggestCategoryName(parsed.merchant, parsed.note)
        val catId = cats.find { it.name == cat }?.id
        val detected = DetectedTransaction(
            source = source, layer = layer, parsed = parsed,
            suggestedCategoryId = catId, suggestedAmount = parsed.amount ?: 0
        )
        _state.update {
            it.copy(detectedList = listOf(detected) + it.detectedList)
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
                    note = detected.parsed.note ?: detected.parsed.merchant ?: "",
                    date = System.currentTimeMillis()
                )
            )
            // 更新日志状态
            CaptureLogManager.updateStatus(detected.id, CaptureLogManager.Status.SAVED)
            _state.update {
                it.copy(detectedList = it.detectedList - detected)
            }
        }
    }

    fun dismissTransaction(detected: DetectedTransaction) {
        CaptureLogManager.updateStatus(detected.id, CaptureLogManager.Status.DISMISSED)
        _state.update { it.copy(detectedList = it.detectedList - detected) }
    }

    fun clearAll() {
        CaptureLogManager.clear()
        _state.update { it.copy(detectedList = emptyList()) }
    }
}
