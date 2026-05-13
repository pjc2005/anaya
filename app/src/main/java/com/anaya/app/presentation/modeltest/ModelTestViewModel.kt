package com.anaya.app.presentation.modeltest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anaya.app.ml.LocalModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

data class ModelTestUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            text = "输入消息发送给手机本地的 0.5B 模型验证是否正常运行。首次发送可能需要 10-30 秒（启动服务）。",
            isUser = false
        )
    ),
    val inputText: String = "",
    val isSending: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ModelTestViewModel @Inject constructor(
    private val localModel: LocalModelManager
) : ViewModel() {

    private val _state = MutableStateFlow(ModelTestUiState())
    val state: StateFlow<ModelTestUiState> = _state.asStateFlow()

    fun onInputChanged(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }

    fun send() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isSending) return

        val userMsg = ChatMessage(text = text, isUser = true)
        val loadingMsg = ChatMessage(text = "", isUser = false, isLoading = true)

        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg + loadingMsg,
            inputText = "",
            isSending = true,
            error = null
        )

        viewModelScope.launch {
            val response = localModel.chat(text)
            val msgs = _state.value.messages.toMutableList()
            msgs.removeAt(msgs.lastIndex) // remove loading

            if (response != null) {
                msgs.add(ChatMessage(text = response, isUser = false))
                _state.value = _state.value.copy(
                    messages = msgs,
                    isSending = false
                )
            } else {
                msgs.add(
                    ChatMessage(
                        text = "⚠️ 模型未响应。请检查：\n1. 模型服务是否正在启动（首次需 20-30 秒）\n2. 返回设置页查看模型状态",
                        isUser = false
                    )
                )
                _state.value = _state.value.copy(
                    messages = msgs,
                    isSending = false,
                    error = "模型未响应"
                )
            }
        }
    }
}
