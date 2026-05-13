package com.anaya.app.service

import android.graphics.Bitmap
import android.util.Log
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.ParsedTransaction
import com.anaya.app.ml.RuleBasedParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class OCRProcessor @Inject constructor(
    private val localModel: LocalModelInterface
) {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                cont.resume(result.text)
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Recognition failed", e)
                cont.resume("")
            }
    }

    suspend fun parseReceipt(bitmap: Bitmap): ParsedTransaction {
        val text = recognizeText(bitmap)
        return if (text.isNotBlank()) {
            // 先尝试用 LLM 解析（更精准）
            val llmResult = localModel.parsePaymentText(text)
            if (llmResult.amount != null && llmResult.confidence >= 0.5f) {
                llmResult.copy(note = llmResult.note ?: text.take(100))
            } else {
                // LLM 不可用或置信度低 → 规则回退
                val parsed = RuleBasedParser.parsePaymentText(text)
                parsed.copy(note = parsed.note ?: text.take(100))
            }
        } else {
            ParsedTransaction(confidence = 0f)
        }
    }

    fun shutdown() {
        recognizer.close()
    }
}
