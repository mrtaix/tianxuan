package top.wkbin.tianxuan

import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.onboarding.OnboardingScreen
import top.wkbin.tianxuan.ui.onboarding.OnboardingViewModel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.datastore.AppearancePreferences
import top.wkbin.tianxuan.core.model.AppUpdateInfo
import top.wkbin.tianxuan.core.network.AppUpdateManager
import top.wkbin.tianxuan.runtime.service.RuntimeServiceController
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.MarkdownText
import top.wkbin.tianxuan.ui.navigation.TianXuanNavHost
import top.wkbin.tianxuan.ui.theme.TianXuanTheme
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import top.wkbin.tianxuan.core.common.navigation.AppNavigationTarget
import top.wkbin.tianxuan.core.common.navigation.GlobalNavigationBus
import top.wkbin.tianxuan.harness.mcp.oauth.McpOAuthCoordinator
import top.wkbin.tianxuan.harness.mcp.McpManager
import top.wkbin.tianxuan.service.adb.AdbNotificationManager

class MainActivity : AppCompatActivity() {
    val settingsDataStore: AppearancePreferences by inject()

    val appUpdateManager: AppUpdateManager by inject()

    val runtimeServiceController: RuntimeServiceController by inject()

    val globalNavigationBus: GlobalNavigationBus by inject()

    val adbNotificationManager: AdbNotificationManager by inject()

    val mcpOAuthCoordinator: McpOAuthCoordinator by inject()

    val mcpManager: McpManager by inject()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) runtimeServiceController.start()
    }

    /** Đã yêu cầu quyền POST_NOTIFICATIONS hay chưa; tách khỏi khởi động dịch vụ để có thể thử lại sau khi cấp quyền. */
    private var notificationPermissionRequested = false

    /** Giữ SplashScreen cho đến khi tải xong tùy chọn hướng dẫn ban đầu từ DataStore để tránh màn hình trắng. */
    private val keepSplashOnScreen: MutableState<Boolean> = mutableStateOf(true)

    private var createdUptimeMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        createdUptimeMs = android.os.SystemClock.uptimeMillis()
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen.value }
        super.onCreate(savedInstanceState)
        initializeAppLanguage(this)
        handleNavigationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val themeStyle by settingsDataStore.themeStyle.collectAsStateWithLifecycle(initialValue = "material_you")
            val dynamicColorEnabled by settingsDataStore.dynamicColorEnabled.collectAsStateWithLifecycle(initialValue = true)
            val chengmingBackgroundUri by settingsDataStore.chengmingBackgroundUri.collectAsStateWithLifecycle(initialValue = null)
            val pageScale by settingsDataStore.appFontScale.collectAsStateWithLifecycle(initialValue = 1f)
            val systemDark = isSystemInDarkTheme()
            val systemDensity = LocalDensity.current
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = systemDensity.density * pageScale.coerceIn(0.8f, 1.3f),
                    fontScale = systemDensity.fontScale,
                ),
            ) {
                TianXuanTheme(
                    style = top.wkbin.tianxuan.ui.theme.ThemeStyle.fromId(themeStyle),
                    darkTheme = isDark,
                    dynamicColor = dynamicColorEnabled,
                    backgroundUri = chengmingBackgroundUri,
                ) {
                val onboardingViewModel: OnboardingViewModel = koinViewModel()
                val onboarding by onboardingViewModel.status.collectAsStateWithLifecycle()

                // Đóng SplashScreen khi đọc xong tùy chọn hướng dẫn; Runtime tiếp tục khôi phục trong nền, không chặn khung hình đầu.
                // OnboardingViewModel.init tự gọi restoreInstalledState để khôi phục Linux; không gọi lại tại đây.
                LaunchedEffect(onboarding.loaded) {
                    if (onboarding.loaded && keepSplashOnScreen.value) {
                        keepSplashOnScreen.value = false
                        android.util.Log.i(
                            "TianXuanStartup",
                            "splash dismissed in ${android.os.SystemClock.uptimeMillis() - createdUptimeMs}ms",
                        )
                    }
                }

                // Kiểm tra cập nhật âm thầm khi khởi động.
                var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
                var downloadProgress by remember { mutableStateOf<Float?>(null) }
                var isDownloading by remember { mutableStateOf(false) }
                var downloadFailed by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val mainContext = LocalContext.current
                val currentVersionName = remember {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mainContext.packageManager.getPackageInfo(
                                mainContext.packageName,
                                PackageManager.PackageInfoFlags.of(0),
                            ).versionName
                        } else {
                            @Suppress("DEPRECATION")
                            mainContext.packageManager.getPackageInfo(mainContext.packageName, 0).versionName
                        }
                    } catch (_: Exception) {
                        null
                    } ?: "0.0.0"
                }

                LaunchedEffect(onboarding.completed) {
                    if (onboarding.completed) {
                        val autoCheck = settingsDataStore.autoCheckUpdates.first()
                        if (autoCheck) {
                            val res = appUpdateManager.checkUpdate(currentVersionName)
                            res.onSuccess { info ->
                                if (info.hasUpdate) updateInfo = info
                            }
                        }
                    }
                }

                updateInfo?.let { info ->
                    RuntimeAlertDialog(
                        onDismissRequest = { if (!isDownloading) updateInfo = null },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Refresh,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(stringResource(R.string.tianxuan_update_available, info.latestVersion), fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.tianxuan_update_versions, info.currentVersion, info.latestVersion),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                                if (info.releaseNotes.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        MarkdownText(
                                            markdown = info.releaseNotes,
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }
                                if (isDownloading) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(R.string.tianxuan_update_downloading_package), style = MaterialTheme.typography.labelMedium)
                                        if (downloadProgress != null) {
                                            LinearProgressIndicator(
                                                progress = { downloadProgress ?: 0f },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                                if (downloadFailed) {
                                    // Phải hiển thị lỗi tải xuống, không dừng âm thầm.
                                    Text(
                                        text = stringResource(R.string.tianxuan_update_download_failed),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            val apkUrl = info.apkDownloadUrl
                            if (apkUrl != null) {
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        downloadProgress = 0f
                                        downloadFailed = false
                                        scope.launch {
                                            val res = appUpdateManager.downloadApk(apkUrl) { dl, tot ->
                                                if (tot != null && tot > 0) downloadProgress = dl.toFloat() / tot.toFloat()
                                            }
                                            isDownloading = false
                                            res.mapCatching { apkFile ->
                                                downloadProgress = 1f
                                                appUpdateManager.installApk(apkFile)
                                                updateInfo = null
                                            }.onFailure {
                                                // Khi tải thất bại, hiển thị lỗi ngay trong hộp thoại cập nhật theo mẫu UI hiện có.
                                                downloadFailed = true
                                            }
                                        }
                                    },
                                    enabled = !isDownloading,
                                ) {
                                    Text(stringResource(if (isDownloading) R.string.tianxuan_downloading else R.string.tianxuan_update_now))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        runCatching {
                                            startActivity(Intent(Intent.ACTION_VIEW,
                                                info.releaseUrl.toUri()))
                                        }
                                        updateInfo = null
                                    },
                                ) {
                                    Text(stringResource(R.string.tianxuan_open_github))
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { updateInfo = null },
                                enabled = !isDownloading,
                            ) {
                                Text(stringResource(R.string.tianxuan_later))
                            }
                        },
                    )
                }

                when {
                    !onboarding.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    onboarding.completed -> TianXuanNavHost(globalNavigationBus = globalNavigationBus)
                    else -> OnboardingScreen(onboardingViewModel)
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Tự đóng cửa sổ nổi Trí Khu khi ứng dụng trở lại tiền cảnh để tránh chồng lên giao diện chính.
        runCatching {
            top.wkbin.tianxuan.ui.chat.floating.FloatingChatService.stop(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(targetIntent: Intent?) {
        val intentToHandle = targetIntent ?: intent ?: return
        val oauthUri = intentToHandle.data
        if (intentToHandle.action == Intent.ACTION_VIEW && oauthUri != null &&
            oauthUri.scheme == "tianxuan" && oauthUri.host == "oauth" && oauthUri.path == "/mcp"
        ) {
            lifecycleScope.launch {
                runCatching { mcpOAuthCoordinator.callback(oauthUri) }
                    .onSuccess { result ->
                        if (result is McpOAuthCoordinator.CallbackResult.Authorized) {
                            lifecycleScope.launch { mcpManager.invalidateServer(result.serverId) }
                        }
                    }
                    .onFailure { /* UI theo dõi trạng thái xác thực máy chủ; tuyệt đối không ghi mã/token callback vào log. */ }
            }
            return
        }
        val action = intentToHandle.action
        val navigateTo = intentToHandle.getStringExtra("navigate_to")
        val isAdbLogcat = action == "top.wkbin.tianxuan.action.OPEN_ADB_LOGCAT" || navigateTo == "adb_logcat"
        if (isAdbLogcat) {
            globalNavigationBus.navigateTo(top.wkbin.tianxuan.core.common.navigation.AppNavigationTarget.AdbLogcat)
        }
        // Khi nhấn thông báo luồng công việc, mở trang chạy và tìm lần thực thi tương ứng.
        if (action == top.wkbin.tianxuan.service.WorkflowForegroundService.ACTION_OPEN_RUN) {
            globalNavigationBus.navigateTo(
                top.wkbin.tianxuan.core.common.navigation.AppNavigationTarget.WorkflowRun(
                    intentToHandle.getStringExtra(top.wkbin.tianxuan.service.WorkflowForegroundService.EXTRA_EXECUTION_ID),
                ),
            )
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        // Hộp thoại quyền thời gian chạy đáng tin cậy nhất sau khi trang đầu được tiếp tục và vẽ xong.
        // Lần mở đầu còn khôi phục hướng dẫn và giao diện; một số bản Android có thể bỏ qua yêu cầu
        // từ onCreate nếu Activity chưa tương tác được hoàn toàn.
        // Thử duy trì dịch vụ mỗi lần trở lại tiền cảnh vì người dùng có thể vừa cấp quyền trong cài đặt hệ thống.
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                // MIUI từ chối AppOp START_FOREGROUND khi chưa cấp quyền POST_NOTIFICATIONS.
                // Hoàn tất kiểm tra quyền trước khi khởi động Runtime cần thông báo thường trực,
                // tránh dịch vụ không có thông báo và hiệu ứng chuyển cảnh hệ thống bất thường.
                startRuntimeServiceWhenNotificationAllowed()
            }
        }
    }

    private fun startRuntimeServiceWhenNotificationAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runtimeServiceController.start()
        } else if (!notificationPermissionRequested) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
