package com.anaya.app.di

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val PREFS_NAME = "anaya_setup"
private const val KEY_SETUP_COMPLETE = "setup_complete"
private const val KEY_SKIP_MODEL = "skip_model"

data class SetupPrefs(
    val isSetupComplete: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val modelDownloaded: Boolean = false,
    val skipModelDownload: Boolean = false
)

@Module
@InstallIn(SingletonComponent::class)
object SetupModule {

    @Provides
    @Singleton
    fun provideSetupPrefs(@ApplicationContext context: Context): SetupPrefsManager {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return SetupPrefsManager(sp, accessibilityManager, context)
    }
}

class SetupPrefsManager(
    private val sp: SharedPreferences,
    private val accessibilityManager: AccessibilityManager,
    private val context: Context
) {
    fun isSetupComplete(): Boolean = sp.getBoolean(KEY_SETUP_COMPLETE, false)

    fun markSetupComplete() {
        sp.edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
    }

    fun resetSetup() {
        sp.edit().clear().apply()
    }

    fun isSkipModel(): Boolean = sp.getBoolean(KEY_SKIP_MODEL, false)

    fun setSkipModel(skip: Boolean) {
        sp.edit().putBoolean(KEY_SKIP_MODEL, skip).apply()
    }

    /** 检测无障碍服务是否已启用 */
    fun isAccessibilityEnabled(): Boolean {
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any { service ->
            service.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
    }

    /** 获取完整的 setup 检查结果 */
    fun getSetupStatus(): SetupPrefs {
        return SetupPrefs(
            isSetupComplete = isSetupComplete(),
            accessibilityEnabled = isAccessibilityEnabled(),
            modelDownloaded = false, // 由调用者更新
            skipModelDownload = isSkipModel()
        )
    }
}
