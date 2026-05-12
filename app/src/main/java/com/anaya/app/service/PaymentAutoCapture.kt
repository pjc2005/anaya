package com.anaya.app.service

import android.util.Log
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.ParsedTransaction
import com.anaya.app.ml.RuleBasedClassifier
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
    private val transactionRepository: TransactionRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startMonitoring(): Flow<ParsedTransaction?> {
        scope.launch {
            clipboardMonitor.clipboardFlow.collect { parsed ->
                if (parsed != null && parsed.amount != null && parsed.confidence >= 0.3f) {
                    autoSaveTransaction(parsed)
                }
            }
        }
        return clipboardMonitor.clipboardFlow
    }

    fun autoSaveTransaction(parsed: ParsedTransaction) {
        val amount = parsed.amount ?: return
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                transactionRepository.insert(
                    Transaction(
                        amount = amount,
                        type = TransactionType.EXPENSE,
                        categoryId = 0,
                        accountId = 1,
                        note = parsed.note ?: parsed.merchant,
                        date = now
                    )
                )
                Log.d("AutoCapture", "Saved: ${parsed.merchant} ¥${amount / 100.0}")
            } catch (e: Exception) {
                Log.e("AutoCapture", "Failed to auto-save", e)
            }
        }
    }
}
