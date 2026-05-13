package com.anaya.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.anaya.app.ml.ParsedTransaction
import com.anaya.app.ml.RuleBasedParser
import java.io.File
import java.io.FileOutputStream

/**
 * 截图OCR兜底 — 用于国产ROM上 rootInActiveWindow 为 null 时的最终降级方案
 *
 * 三层兜底链路：
 *   1. AccessibilityService.takeScreenshot() (API 34+)
 *   2. screencap shell 命令 (root 设备)
 *   3. 返回空（由调用方继续使用事件文本解析）
 */
class ScreenshotFallback(private val service: AccessibilityService) {

    private val TAG = "ScreenshotFallback"

    /** 是否可在当前设备使用 */
    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 34 || isRootAvailable()

    /**
     * 尝试截图并进行OCR识别
     * @return 识别结果，失败返回 null
     */
    suspend fun captureAndOcr(ocr: OCRProcessor): ParsedTransaction? {
        val bitmap = captureScreen() ?: return null
        Log.d(TAG, "Screenshot captured: ${bitmap.width}x${bitmap.height}")

        val text = ocr.recognizeText(bitmap)
        bitmap.recycle()

        if (text.isBlank()) {
            Log.w(TAG, "OCR returned empty text")
            return null
        }

        Log.d(TAG, "OCR text (${text.length} chars): ${text.take(200)}")
        val parsed = RuleBasedParser.parsePaymentText(text)
        if (parsed.amount != null && parsed.confidence >= 0.4f) {
            Log.i(TAG, "OCR parsed: amount=${parsed.amount / 100.0} merchant=${parsed.merchant}")
            return parsed.copy(note = parsed.note ?: text.take(100))
        }
        return null
    }

    // ═══════════════════════════════════════════════════
    //  截图
    // ═══════════════════════════════════════════════════

    private fun captureScreen(): Bitmap? {
        // 方式一: API 34+ AccessibilityService.takeScreenshot
        if (Build.VERSION.SDK_INT >= 34) {
            return captureViaAccessibilityService()
        }

        // 方式二: shell screencap (root 或有 shell 权限)
        return captureViaScreencap()
    }

    @RequiresApi(34)
    private fun captureViaAccessibilityService(): Bitmap? {
        // AccessibilityService.takeScreenshot 是异步回调，但我们的调用是 suspend
        // 用 suspendCancellableCoroutine 包装
        return captureViaScreencap() // 先走 screencap 方式作为统一方案
    }

    /**
     * 使用 screencap shell 命令截图
     * 在 root 设备上可用，或通过 adb shell
     */
    private fun captureViaScreencap(): Bitmap? {
        val file = File(service.cacheDir, "anaya_screenshot.png")
        return try {
            // 写入临时文件
            val process = Runtime.getRuntime().exec("sh -c screencap -p ${file.absolutePath}")
            process.waitFor()

            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "screencap produced no output")
                return null
            }

            BitmapFactory.decodeFile(file.absolutePath)?.also {
                Log.d(TAG, "screencap OK: ${file.length()} bytes")
            }
        } catch (e: Exception) {
            Log.w(TAG, "screencap failed", e)
            null
        } finally {
            if (file.exists()) file.delete()
        }
    }

    /**
     * 保存截图到缓存目录（调试用）
     */
    fun saveDebugScreenshot(): String? {
        val bitmap = captureScreen() ?: return null
        val file = File(service.cacheDir, "debug_screenshot_${System.currentTimeMillis()}.png")
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            Log.d(TAG, "Debug screenshot saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Save debug screenshot failed", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private fun isRootAvailable(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec("which screencap")
                val reader = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                reader.isNotBlank()
            } catch (_: Exception) {
                false
            }
        }
    }
}
