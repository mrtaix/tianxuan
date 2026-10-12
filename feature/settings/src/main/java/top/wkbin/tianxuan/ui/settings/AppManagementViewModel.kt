package top.wkbin.tianxuan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.database.AndroidAppEntity
import top.wkbin.tianxuan.core.database.AndroidAppRepository
import top.wkbin.tianxuan.runtime.apps.AndroidAppManager

class AppManagementViewModel(
    repository: AndroidAppRepository,
    private val appManager: AndroidAppManager,
) : ViewModel() {
    val apps: StateFlow<List<AndroidAppEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 当前 message 是否为失败结果（类型化标记，避免 UI 用字符串匹配判断样式）。 */
    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    fun synchronize() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            val result = appManager.synchronize()
            _syncing.value = false
            _message.value = result.fold(
                onSuccess = { "Đã đồng bộ ${it.total} ứng dụng: ${it.userApps} ứng dụng người dùng, ${it.systemApps} ứng dụng hệ thống." },
                onFailure = { it.message ?: "Đồng bộ ứng dụng thất bại" },
            )
            _messageIsError.value = result.isFailure
        }
    }

    fun consumeMessage() { _message.value = null }
}
