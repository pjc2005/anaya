package com.anaya.app.service

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.ParsedTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localModel: LocalModelInterface
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastText: String? = null

    val clipboardFlow: Flow<ParsedTransaction?> = callbackFlow {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (text != null && text != lastText) {
                    lastText = text
                    Log.d("Clipboard", "Detected: $text")
                    trySend(localModel.parsePaymentText(text))
                }
            }
        }
        clipboard.addPrimaryClipChangedListener(listener)
        awaitClose { clipboard.removePrimaryClipChangedListener(listener) }
    }.distinctUntilChanged()
}
