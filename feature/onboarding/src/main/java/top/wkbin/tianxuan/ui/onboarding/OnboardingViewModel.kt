package top.wkbin.tianxuan.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.tianxuan.core.datastore.OnboardingPreferences
import top.wkbin.tianxuan.core.model.RuntimeState
import top.wkbin.tianxuan.core.tools.ProviderRepository
import top.wkbin.tianxuan.core.tools.AgentProviderCatalog
import top.wkbin.tianxuan.core.tools.AgentModelDiscovery
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.RegistryRoute
import top.wkbin.tianxuan.runtime.RuntimeInstallRequest
import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import top.wkbin.tianxuan.core.tools.ProviderEndpointPolicy

data class OnboardingStatus(val loaded: Boolean = false, val completed: Boolean = false)

class OnboardingViewModel(
    private val context: Context,
    private val settings: OnboardingPreferences,
    private val linuxRuntime: LinuxRuntime,
    private val providerRepository: ProviderRepository,
    private val providerCatalogRepository: AgentProviderCatalog,
    private val modelDiscovery: AgentModelDiscovery,
    private val toolManager: top.wkbin.tianxuan.core.tools.ToolManager,
    private val profileWriter: top.wkbin.tianxuan.core.tools.AiProfileWriter,
    private val profileBackupCodec: top.wkbin.tianxuan.core.tools.AiProfileBackupCodec,
) : ViewModel() {
    val providerCatalog = providerCatalogRepository.providers

    /** Tín hiệu đọc xong tùy chọn DataStore: màn hìnhSplash chỉ chờ ở đây (mili-giây), không chờ khôi phục Linux Runtime. */
    private val prefsLoaded = MutableStateFlow(false)
    val status: StateFlow<OnboardingStatus> = combine(
        prefsLoaded,
        settings.onboardingCompleted,
    ) { loaded, completed ->
        // completed không còn phụ thuộc RuntimeState.Ready: người dùng đã hoàn tất hướng dẫn vào thẳng giao diện chính,
        // khôi phục sandbox tiếp tục trong nền; trang chủ/trợ lý đều đã có tiến độ khởi tạo và trạng thái lỗi riêng.
        OnboardingStatus(loaded = loaded, completed = completed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingStatus())
    val runtimeState: StateFlow<RuntimeState> = linuxRuntime.state

    val devSuites: List<top.wkbin.tianxuan.core.model.PluginBundle> = top.wkbin.tianxuan.core.model.BuiltinPluginBundles.bundles

    private val _selectedSuites = MutableStateFlow<Set<String>>(
        top.wkbin.tianxuan.core.model.BuiltinPluginBundles.bundles.map { it.id }.toSet(),
    )
    val selectedSuites = _selectedSuites.asStateFlow()

    private val _isInstallingPlugins = MutableStateFlow(false)
    val isInstallingPlugins = _isInstallingPlugins.asStateFlow()

    private val _pluginInstallProgress = MutableStateFlow<String?>(null)
    val pluginInstallProgress = _pluginInstallProgress.asStateFlow()

    private val _distribution = MutableStateFlow("ubuntu")
    val distribution = _distribution.asStateFlow()
    private val _mirror = MutableStateFlow("auto")
    val mirror = _mirror.asStateFlow()
    private val _page = MutableStateFlow(0)
    val page = _page.asStateFlow()
    private val _modelProvider = MutableStateFlow("Giao diện tương thích OpenAI tùy chỉnh")
    val modelProvider = _modelProvider.asStateFlow()
    private val _modelName = MutableStateFlow("")
    val modelName = _modelName.asStateFlow()
    private val _modelId = MutableStateFlow("")
    val modelId = _modelId.asStateFlow()
    private val _baseUrl = MutableStateFlow("")
    val baseUrl = _baseUrl.asStateFlow()
    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()
    private val _importedProfile = MutableStateFlow<top.wkbin.tianxuan.core.model.AiModelProfileExport?>(null)
    val importedProfile = _importedProfile.asStateFlow()
    private val _discoveredModels = MutableStateFlow<List<String>>(emptyList())
    val discoveredModels = _discoveredModels.asStateFlow()
    private val _discoveringModels = MutableStateFlow(false)
    val discoveringModels = _discoveringModels.asStateFlow()
    private val _modelDiscoveryError = MutableStateFlow<String?>(null)
    val modelDiscoveryError = _modelDiscoveryError.asStateFlow()
    private var discoveryDebounceJob: Job? = null
    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()
    private val _importError = MutableStateFlow<String?>(null)
    val importError = _importError.asStateFlow()

    init {
        viewModelScope.launch {
            _distribution.value = settings.selectedDistribution.first()
            _mirror.value = settings.mirrorPolicy.first()
            prefsLoaded.value = true
        }
        viewModelScope.launch {
            restoreInstalledState()
        }
    }

    fun selectDistribution(distribution: String) {
        _distribution.value = distribution
        viewModelScope.launch { settings.setSelectedDistribution(distribution) }
    }

    fun selectMirror(mirror: String) {
        _mirror.value = mirror
        viewModelScope.launch { settings.setMirrorPolicy(mirror) }
    }

    fun setPage(page: Int) {
        _page.value = page
    }

    fun setModelProvider(provider: String) {
        _modelProvider.value = provider
    }

    fun setModelId(id: String) {
        _modelId.value = id
    }

    fun setBaseUrl(url: String) {
        _baseUrl.value = url
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    fun selectProvider(id: String) {
        val preset = providerCatalogRepository.find(id)
        _modelProvider.value = preset.name
        _baseUrl.value = preset.baseUrl
        _modelId.value = preset.recommendedModels.firstOrNull().orEmpty()
        _discoveredModels.value = emptyList()
        _modelDiscoveryError.value = null
    }

    private fun scheduleModelDiscovery(delayMillis: Long = 600) {
        discoveryDebounceJob?.cancel()
        val cleanUrl = ProviderEndpointPolicy.normalizeUrl(_baseUrl.value)
        if (!ProviderEndpointPolicy.isSafeBaseUrl(cleanUrl)) return
        discoveryDebounceJob = viewModelScope.launch {
            delay(delayMillis)
            discoveryDebounceJob = null
            startModelDiscovery(cleanUrl)
        }
    }

    fun discoverModels() {
        discoveryDebounceJob?.cancel()
        discoveryDebounceJob = null
        val cleanUrl = ProviderEndpointPolicy.normalizeUrl(_baseUrl.value)
        if (!ProviderEndpointPolicy.isSafeBaseUrl(cleanUrl)) return
        startModelDiscovery(cleanUrl)
    }

    private fun startModelDiscovery(cleanUrl: String) {
        _discoveringModels.value = true
        _modelDiscoveryError.value = null
        _discoveredModels.value = emptyList()
        viewModelScope.launch {
            val preset = providerCatalog.firstOrNull { it.name == _modelProvider.value } ?: providerCatalogRepository.find("custom")
            runCatching { modelDiscovery.discover(preset, cleanUrl, _apiKey.value.ifBlank { null }) }
                .onSuccess { models ->
                    _discoveredModels.value = models
                    models.firstOrNull()?.let { _modelId.value = it }
                }
                .onFailure { _modelDiscoveryError.value = it.message ?: "Làm mới danh sách mô hình thất bại" }
            _discoveringModels.value = false
        }
    }

    fun install() {
        if (runtimeState.value is RuntimeState.Initializing) return
        viewModelScope.launch {
            settings.setSelectedDistribution(_distribution.value)
            settings.setMirrorPolicy(_mirror.value)
            linuxRuntime.initialize(
                RuntimeInstallRequest(
                    distributionId = _distribution.value,
                    registryRoute = when (_mirror.value) {
                        "official" -> RegistryRoute.OFFICIAL
                        "china" -> RegistryRoute.CHINA_ACCELERATED
                        else -> RegistryRoute.AUTO
                    },
                ),
            )
            if (linuxRuntime.state.value is RuntimeState.Ready) _page.value = 1
        }
    }

    fun importArchive(uri: Uri) {
        if (runtimeState.value is RuntimeState.Initializing || _isImporting.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val imported = File(context.cacheDir, "rootfs-import-${System.currentTimeMillis()}")
            _isImporting.value = true
            _importError.value = null
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    imported.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Không thể đọc tệp đã chọn")
                settings.setSelectedDistribution(_distribution.value)
                settings.setMirrorPolicy(_mirror.value)
                val result = linuxRuntime.importDistro(
                    RuntimeInstallRequest(distributionId = _distribution.value),
                    imported,
                )
                result.errorOrNull()?.let { error ->
                    _importError.value = error.message
                }
                if (linuxRuntime.state.value is RuntimeState.Ready) _page.value = 1
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _importError.value = throwable.message ?: "Không thể nhập tệp RootFS đã chọn"
            } finally {
                imported.delete()
                _isImporting.value = false
            }
        }
    }

    fun restoreInstalledState() {
        viewModelScope.launch {
            // Khôi phục môi trường Linux đã cài trong nền: các việc nặng như dò sức khỏe PRoot, cấu hình mirror không chặn khung hình đầu.
            linuxRuntime.restoreInstalledState()
            if (!settings.onboardingCompleted.first() && linuxRuntime.state.value is RuntimeState.Ready) {
                _page.value = 1
            }
        }
    }

    fun retryReady() {
        if (linuxRuntime.state.value is RuntimeState.Initializing) return
        restoreInstalledState()
    }

    fun toggleSuite(id: String) {
        val current = _selectedSuites.value
        _selectedSuites.value = if (id in current) current - id else current + id
    }

    fun skipSuites() {
        _page.value = 2
    }

    fun installSelectedSuitesAndProceed() {
        val selected = _selectedSuites.value.toSet()
        _page.value = 2
        if (selected.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isInstallingPlugins.value = true
            try {
                toolManager.batchInstallSuites(selected).collect { event ->
                    if (event is top.wkbin.tianxuan.runtime.tools.InstallEvent.Progress) {
                        _pluginInstallProgress.value = event.message
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // phải truyền tiếp ngoại lệ hủy
            } catch (e: Exception) {
                // Bỏ qua lỗi để luồng chạy mượt
            } finally {
                _isInstallingPlugins.value = false
                _pluginInstallProgress.value = null
            }
        }
    }

    fun setModelName(name: String) {
        _modelName.value = name
    }

    fun importProfileFromJson(rawJson: String): Result<top.wkbin.tianxuan.core.model.AiModelProfileExport> {
        return profileBackupCodec.parseProfiles(rawJson).mapCatching { profiles ->
            val profile = profiles.firstOrNull() ?: error("Gói nhập không chứa hồ sơ mô hình nào")
            require(profile.model.isNotBlank() || profile.name.isNotBlank()) { "Cấu hình mô hình thiếu tên hoặc ID" }

            _modelProvider.value = profile.provider.ifBlank { "OpenAI" }
            _baseUrl.value = profile.baseUrl
            val effectiveKey = profile.apiKeys.firstOrNull() ?: profile.apiKey.orEmpty()
            _apiKey.value = effectiveKey
            val primaryModel = profile.model.split(",").firstOrNull { it.isNotBlank() }?.trim() ?: profile.model
            _modelId.value = primaryModel.ifBlank { profile.name }
            _modelName.value = profile.name
            _importedProfile.value = profile
            profile
        }
    }

    fun skipModel() = finish()

    fun saveModelAndFinish() {
        val model = _modelId.value.trim()
        if (model.isBlank()) return
        val imported = _importedProfile.value
        viewModelScope.launch {
            try {
                val keyToSave = if (_apiKey.value.isNotBlank()) {
                    _apiKey.value.trim()
                } else if (imported != null && imported.apiKeys.isNotEmpty()) {
                    imported.apiKeys.joinToString("\n")
                } else {
                    imported?.apiKey.orEmpty()
                }
                val modelList = if (imported != null && imported.model.isNotBlank()) {
                    imported.model
                } else {
                    model
                }
                profileWriter.upsertProfile(
                    top.wkbin.tianxuan.core.tools.AiProfileWriter.UpsertRequest(
                        name = _modelName.value.ifBlank { model },
                        provider = _modelProvider.value,
                        model = modelList,
                        baseUrl = _baseUrl.value,
                        apiKey = keyToSave,
                        requestsPerMinutePerKey = imported?.requestsPerMinutePerKey ?: 0,
                        temperature = imported?.temperature,
                        maxTokens = imported?.maxTokens,
                        topP = imported?.topP,
                        reasoningMode = imported?.reasoningMode,
                        reasoningEffort = imported?.reasoningEffort,
                        toolCallMode = imported?.toolCallMode,
                        contextTokens = imported?.contextTokens,
                        customHeaders = imported?.customHeaders.orEmpty(),
                        pureChatMode = imported?.pureChatMode ?: false,
                        visionEnabled = imported?.visionEnabled ?: true,
                        imageGenerationEnabled = imported?.imageGenerationEnabled ?: false,
                        responseApiEnabled = imported?.responseApiEnabled ?: false,
                    ),
                )
                finish()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nếu ghi thất bại thì ở lại trang hướng dẫn và hiện thông báo, không âm thầm đánh dẫn đã hoàn tất onboarding
                _importError.value = "Lưu cấu hình mô hình thất bại: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    private fun finish() {
        viewModelScope.launch { settings.setOnboardingCompleted(true) }
    }
}
