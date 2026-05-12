package com.anaya.app.service

import android.graphics.Bitmap
import android.util.Log
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
class OCRProcessor @Inject constructor() {

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
            val parsed = RuleBasedParser.parsePaymentText(text)
            parsed.copy(note = parsed.note ?: text.take(100))
        } else {
            ParsedTransaction(confidence = 0f)
        }
    }

    fun shutdown() {
        recognizer.close()
    }
}
