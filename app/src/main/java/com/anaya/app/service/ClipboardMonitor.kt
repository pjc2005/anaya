package com.anaya.app.service

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.ParsedTransaction
import com.anaya.app.ml.RuleBasedParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localModel: LocalModelInterface
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastText: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val clipboardFlow: Flow<ParsedTransaction?> = callbackFlow {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (text != null && text != lastText) {
                    lastText = text
                    Log.d("Clipboard", "Detected: ${text.take(100)}")
                    // 异步 LLM 解析 + 规则回退
                    scope.launch {
                        val parsed = localModel.parsePaymentText(text)
                        if (parsed.amount != null && parsed.confidence >= 0.4f) {
                            Log.i("Clipboard", "LLM parsed: ${parsed.amount / 100.0}元 -> ${parsed.merchant}")
                            trySend(parsed)
                        } else {
                            // LLM 失败 → 规则回退
                            val ruleParsed = RuleBasedParser.parsePaymentText(text)
                            if (ruleParsed.amount != null && ruleParsed.confidence >= 0.3f) {
                                Log.i("Clipboard", "Rule fallback: ${ruleParsed.amount / 100.0}元 -> ${ruleParsed.merchant}")
                                trySend(ruleParsed)
                            }
                        }
                    }
                }
            }
        }
        clipboard.addPrimaryClipChangedListener(listener)
        awaitClose { clipboard.removePrimaryClipChangedListener(listener) }
    }.distinctUntilChanged()
}
