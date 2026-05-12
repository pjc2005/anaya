package com.anaya.app.service

import android.util.Log
import com.anaya.app.data.repository.TransactionRepositoryImpl
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.ParsedTransaction
import com.anaya.app.ml.RuleBasedClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentAutoCapture @Inject constructor(
    private val localModel: LocalModelInterface,
    private val clipboardMonitor: ClipboardMonitor,
    private val transactionRepository: TransactionRepositoryImpl
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startMonitoring(): Flow<ParsedTransaction> {
        scope.launch {
            clipboardMonitor.clipboardFlow.collect { parsed ->
                if (parsed != null && parsed.amount != null && parsed.confidence >= 0.3f) {
                    autoSaveTransaction(parsed)
                }
            }
        }
        return clipboardMonitor.clipboardFlow as Flow<ParsedTransaction>
    }

    fun autoSaveTransaction(parsed: ParsedTransaction) {
        scope.launch {
            try {
                val catName = RuleBasedClassifier.suggestCategoryName(parsed.merchant, parsed.note)
                val now = System.currentTimeMillis()
                transactionRepository.insert(
                    Transaction(
                        amount = parsed.amount ?: return@launch,
                        type = TransactionType.EXPENSE,
                        categoryId = null,
                        accountId = 1,
                        merchant = parsed.merchant,
                        note = parsed.note,
                        date = now
                    )
                )
                Log.d("AutoCapture", "Saved: ${parsed.merchant} ¥${parsed.amount?.div(100.0)}")
            } catch (e: Exception) {
                Log.e("AutoCapture", "Failed to auto-save", e)
            }
        }
    }
}
