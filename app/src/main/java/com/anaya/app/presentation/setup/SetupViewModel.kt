package com.anaya.app.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.di.SetupPrefsManager
import com.anaya.app.ml.LocalModelManager
import com.anaya.app.ml.ModelStatus
import com.anaya.app.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep(val label: String) {
    WELCOME("欢迎"),
    ACCESSIBILITY("无障碍服务"),
    MODEL("AI 模型"),
    COMPLETE("完成")
}

data class SetupUiState(
    val currentStep: SetupStep = SetupStep.WELCOME,
    val accessibilityEnabled: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val downloadProgress: Int = 0,
    val hasAccounts: Boolean = false,
    val isChecking: Boolean = true,
    val isCompleting: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val setupPrefs: SetupPrefsManager,
    private val localModel: LocalModelManager,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        checkStatus()
    }

    private fun checkStatus() {
        viewModelScope.launch {
            // 并行检查所有状态
            val accessibility = setupPrefs.isAccessibilityEnabled()
            localModel.checkModelStatus()
            val accounts = accountRepository.getAllAccounts().first()
            val modelStatus = localModel.modelStatus.value

            _state.update {
                it.copy(
                    accessibilityEnabled = accessibility,
                    modelStatus = modelStatus,
                    hasAccounts = accounts.isNotEmpty(),
                    isChecking = false
                )
            }

            // 如果全部已配置，直接跳完成
            if (accessibility && (modelStatus == ModelStatus.Ready || setupPrefs.isSkipModel())) {
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

    fun nextStep() {
        val current = _state.value.currentStep
        when (current) {
            SetupStep.WELCOME -> {
                _state.update { it.copy(currentStep = SetupStep.ACCESSIBILITY) }
            }
            SetupStep.ACCESSIBILITY -> {
                // 重新检查无障碍状态
                val enabled = setupPrefs.isAccessibilityEnabled()
                _state.update { it.copy(accessibilityEnabled = enabled) }
                // 无论是否启用都继续
                _state.update { it.copy(currentStep = SetupStep.MODEL) }
            }
            SetupStep.MODEL -> {
                // 模型已经下载完成 or 跳过 → 完成
                _state.update { it.copy(currentStep = SetupStep.COMPLETE) }
            }
            SetupStep.COMPLETE -> {
                // 完成设置
                completeSetup()
            }
        }
    }

    fun skipModel() {
        setupPrefs.setSkipModel(true)
        _state.update { it.copy(currentStep = SetupStep.COMPLETE) }
    }

    fun downloadModel() {
        viewModelScope.launch {
            localModel.downloadModel()
        }
    }

    fun refreshAccessibilityStatus() {
        val enabled = setupPrefs.isAccessibilityEnabled()
        _state.update { it.copy(accessibilityEnabled = enabled) }
    }

    /** 打开系统无障碍设置页面 */
    fun openAccessibilitySettings(context: android.content.Context) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun completeSetup() {
        viewModelScope.launch {
            _state.update { it.copy(isCompleting = true) }
            setupPrefs.markSetupComplete()
            _state.update { it.copy(isCompleting = false) }
        }
    }

    /** 重新检查并决定跳转到哪一步（用于从设置页面返回后的恢复检查） */
    fun rerunCheck() {
        viewModelScope.launch {
            val accessibility = setupPrefs.isAccessibilityEnabled()
            localModel.checkModelStatus()
            val modelStatus = localModel.modelStatus.value
            val skipModel = setupPrefs.isSkipModel()

            _state.update {
                it.copy(
                    accessibilityEnabled = accessibility,
                    modelStatus = modelStatus,
                    isChecking = false
                )
            }

            // 如果全部完成，自动跳到完成步
            if (accessibility && (modelStatus == ModelStatus.Ready || skipModel)) {
                _state.update { it.copy(currentStep = SetupStep.COMPLETE) }
            }
        }
    }
}
