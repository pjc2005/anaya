package com.anaya.app.setup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 自启动引导 — 各品牌 Android 的自启动设置入口不同
 *
 * 参考自动记账：自启动可提升无障碍稳定性，请务必开启
 */
object AutoStartHelper {

    fun isManufacturerSupported(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer in listOf("xiaomi", "redmi", "huawei", "honor", "oppo", "vivo", "oneplus", "realme")
    }

    fun openAutoStartSettings(context: Context) {
        val intent = when {
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Redmi", ignoreCase = true) -> {
                // 小米/红米：系统设置 → 应用设置 → 自启动
                Intent().apply {
                    action = "miui.intent.action.OP_AUTO_START"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Honor", ignoreCase = true) -> {
                // 华为/荣耀：应用信息页 → 权限 → 自启动
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
            Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Realme", ignoreCase = true) -> {
                // OPPO/一加/真我
                Intent().apply {
                    action = "com.oppo.safe.action.AUTO_START"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            Build.MANUFACTURER.equals("vivo", ignoreCase = true) -> {
                // vivo：i管家 → 自启动管理
                Intent().apply {
                    action = "com.vivo.safecenter.managerspace.AUTOSTART_MANAGER"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            else -> {
                // 通用：进入应用详情，用户自行查找
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        // 如果特定厂商的 intent 无法处理，fallback 到通用设置页
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
