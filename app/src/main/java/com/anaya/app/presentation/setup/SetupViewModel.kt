package com.anaya.app.presentation.setup

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.di.SetupPrefsManager
import com.anaya.app.ml.LocalModelManager
import com.anaya.app.ml.ModelStatus
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.setup.AutoStartHelper
import com.anaya.app.setup.BatteryOptimizationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置步骤 — 参考自动记账的引导流程
 * 新增：自启动 + 忽略电池优化
 */
enum class SetupStep(val label: String, val description: String) {
    WELCOME("欢迎", "开始设置"),
    ACCESSIBILITY("无障碍服务", "必须开启才能自动识别支付"),
    AUTO_START("自启动", "可提升无障碍稳定性"),
    BATTERY("忽略电池优化", "防止后台被杀"),
    MODEL("AI 模型", "本地智能解析"),
    COMPLETE("完成", "")
}

data class SetupUiState(
    val currentStep: SetupStep = SetupStep.WELCOME,
    val accessibilityEnabled: Boolean = false,
    val autoStartEnabled: Boolean? = null,     // null = 未知 / 不需要
    val batteryOptimizationIgnored: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val downloadProgress: Int = 0,
    val hasAccounts: Boolean = false,
    val isChecking: Boolean = true,
    val isCompleting: Boolean = false
) {
    /** 无障碍相关的步骤是否全部完成 */
    val accessibilityStepsComplete: Boolean get() {
        val autoStartOk = autoStartEnabled != false  // true 或 null 都视为 OK
        return accessibilityEnabled && autoStartOk && batteryOptimizationIgnored
    }
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val setupPrefs: SetupPrefsManager,
    private val localModel: LocalModelManager,
    private val accountRepository: AccountRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        checkStatus()
    }

    private fun checkStatus() {
        viewModelScope.launch {
            val accessibility = setupPrefs.isAccessibilityEnabled()
            localModel.checkModelStatus()
            val accounts = accountRepository.getAllAccounts().first()
            val modelStatus = localModel.modelStatus.value
            val skipModel = setupPrefs.isSkipModel()

            // 只在小米/华为等有自启动管理的品牌检查，否则跳过
            val autoStartInfo = if (AutoStartHelper.isManufacturerSupported()) {
                false  // 厂商支持，默认未设置
            } else {
                null  // 厂商不支持此功能，跳过
            }

            _state.update {
                it.copy(
                    accessibilityEnabled = accessibility,
                    autoStartEnabled = autoStartInfo,
                    batteryOptimizationIgnored = BatteryOptimizationHelper
                        .isIgnoringBatteryOptimizations(appContext),
                    modelStatus = modelStatus,
                    hasAccounts = accounts.isNotEmpty(),
                    isChecking = false
                )
            }

            // 如果全部已配置，直接跳到完成步
            val modelOk = modelStatus == ModelStatus.Ready || skipModel
            val autoStartOk = autoStartInfo != false
            val batteryOk = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(appContext)
            if (accessibility && modelOk && autoStartOk && batteryOk) {
                _state.update { it.copy(currentStep = SetupStep.COMPLETE) }
            }
        }

        // 监听模型下载进度
        viewModelScope.launch {
            localModel.downloadProgress.collect { progress ->
                _state.update { it.copy(downloadProgress = progress) }
            }
        }
        viewModelScope.launch {
            localModel.modelStatus.collect { status ->
                _state.update { it.copy(modelStatus = status) }
            }
        }
    }

    // ── 步骤导航 ──

    fun nextStep() {
        val current = _state.value.currentStep
        when (current) {
            SetupStep.WELCOME -> {
                _state.update { it.copy(currentStep = SetupStep.ACCESSIBILITY) }
            }
            SetupStep.ACCESSIBILITY -> {
                val enabled = setupPrefs.isAccessibilityEnabled()
                _state.update {
                    it.copy(accessibilityEnabled = enabled, currentStep = SetupStep.AUTO_START)
                }
            }
            SetupStep.AUTO_START -> {
                _state.update { it.copy(currentStep = SetupStep.BATTERY) }
            }
            SetupStep.BATTERY -> {
                val ignored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(appContext)
                _state.update {
                    it.copy(batteryOptimizationIgnored = ignored, currentStep = SetupStep.MODEL)
                }
            }
            SetupStep.MODEL -> {
                _state.update { it.copy(currentStep = SetupStep.COMPLETE) }
            }
            SetupStep.COMPLETE -> {
                completeSetup()
            }
        }
    }

    // ── 各步骤的操作 ──

    fun refreshAccessibilityStatus() {
        val enabled = setupPrefs.isAccessibilityEnabled()
        _state.update { it.copy(accessibilityEnabled = enabled) }
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun openAutoStartSettings() {
        AutoStartHelper.openAutoStartSettings(appContext)
    }

    /** 跳过自启动（不支持此功能的手机） */
    fun skipAutoStart() {
        _state.update { it.copy(autoStartEnabled = null) }
    }

    fun openBatteryOptimizationSettings() {
        BatteryOptimizationHelper.openBatteryOptimizationSettings(appContext)
    }

    /** 手动标记电池优化已处理（某些手机可能无法弹出请求） */
    fun markBatteryOptimizationDone() {
        _state.update { it.copy(batteryOptimizationIgnored = true) }
    }

    fun refreshBatteryStatus() {
        val ignored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(appContext)
        _state.update { it.copy(batteryOptimizationIgnored = ignored) }
    }

    // ── 模型下载 ──

    fun skipModel() {
        setupPrefs.setSkipModel(true)
        _state.update { it.copy(currentStep = SetupStep.COMPLETE) }
    }

    fun downloadModel() {
        viewModelScope.launch {
            localModel.downloadModel()
        }
    }

    // ── 完成设置 ──

    fun completeSetup() {
        viewModelScope.launch {
            _state.update { it.copy(isCompleting = true) }
            setupPrefs.markSetupComplete()
            _state.update { it.copy(isCompleting = false) }
        }
    }

    /** 重新检查所有状态（从设置页面返回后） */
    fun rerunCheck() {
        viewModelScope.launch {
            val accessibility = setupPrefs.isAccessibilityEnabled()
            localModel.checkModelStatus()
            val modelStatus = localModel.modelStatus.value
            val skipModel = setupPrefs.isSkipModel()
            val batteryOk = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(appContext)

            _state.update {
                it.copy(
                    accessibilityEnabled = accessibility,
                    modelStatus = modelStatus,
                    batteryOptimizationIgnored = batteryOk,
                    isChecking = false
                )
            }

            if (accessibility && batteryOk && (modelStatus == ModelStatus.Ready || skipModel)) {
                _state.update { it.copy(currentStep = SetupStep.COMPLETE) }
            }
        }
    }
}
