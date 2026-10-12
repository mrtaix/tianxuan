package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.runtime.FtpServiceState
import top.wkbin.tianxuan.ui.components.NoticeBanner
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton
import top.wkbin.tianxuan.ui.components.RuntimeSwitch
import top.wkbin.tianxuan.ui.components.RuntimeTextButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text

@Composable
fun FtpSettingsScreen(
    onBack: () -> Unit,
    viewModel: FtpSettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val operating by viewModel.operating.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val vpnActive by viewModel.vpnActive.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var portText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(settings.port.toString()) }
    var usernameText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(settings.username) }
    var showPasswordDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showUsernameDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.distroId, settings.port) { portText = settings.port.toString() }
    LaunchedEffect(settings.distroId, settings.username) { usernameText = settings.username }

    message?.let { current ->
        RuntimeAlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            title = { Text(if (messageIsError) "Thao tác FTP chưa hoàn tất" else "Dịch vụ tệp từ xa FTP") },
            text = { Text(current) },
            confirmButton = { RuntimeTextButton(onClick = viewModel::consumeMessage) { Text("知道了") } },
        )
    }

    if (showPasswordDialog) {
        FtpPasswordDialog(
            passwordConfigured = settings.passwordConfigured,
            onDismiss = { showPasswordDialog = false },
            onSave = { password ->
                showPasswordDialog = false
                viewModel.savePassword(password)
            },
            onClear = {
                showPasswordDialog = false
                viewModel.clearPassword()
            },
        )
    }

    if (showUsernameDialog) {
        FtpUsernameDialog(
            currentUsername = settings.username,
            onDismiss = { showUsernameDialog = false },
            onSave = { user ->
                showUsernameDialog = false
                viewModel.saveUsername(user)
            },
        )
    }

    val statusText = when (serviceState) {
        is FtpServiceState.Stopped -> "已停止"
        is FtpServiceState.Starting -> "Đang khởi động"
        is FtpServiceState.Running -> "运行中"
        is FtpServiceState.Failed -> "启动失败"
    }
    val busy = operating || serviceState is FtpServiceState.Starting

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("Truy cập tệp từ xa FTP", onBack, statusText = statusText) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NoticeBanner(
                    text = "FTP kết nối trực tiếp hệ thống tệp gốc của container Linux hiện tại (/), trong cùng Wi-Fi hoặc mạng có thể dùng FileZilla, Cyberduck, Windows Explorer trên máy tính để tải lên/tải xuống tệp lớn và quản lý thư mục nhanh chóng.",
                )
            }

            item {
                SectionLabel("Trạng thái dịch vụ")
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Server,
                        title = "Bật dịch vụ FTP",
                        subtitle = "Giữ bật và tự động khởi động cùng Linux runtime",
                        value = if (busy) {
                            "Đang xử lý..."
                        } else {
                            when (serviceState) {
                                is FtpServiceState.Running -> "运行中"
                                is FtpServiceState.Starting -> "启动中"
                                is FtpServiceState.Failed -> "失败"
                                is FtpServiceState.Stopped -> "已停止"
                            }
                        },
                        trailing = {
                            if (busy) {
                                RuntimeCircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                )
                            } else {
                                RuntimeSwitch(
                                    checked = settings.enabled,
                                    onCheckedChange = viewModel::toggleEnabled,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        },
                    )
                }
                (serviceState as? FtpServiceState.Failed)?.let { failed ->
                    RuntimeCard(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                    ) {
                        Text(failed.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                SectionLabel("Địa chỉ kết nối và tham số cấu hình client")
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Tham số kết nối App ngoài (điền từng mục):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ParamRow(
                            label = "Cổng dịch vụ (Port)",
                            value = settings.port.toString(),
                            highlight = true,
                            onCopy = { copyToClipboard(context,  settings.port.toString(), "FTP URL",  "Cổng FTP (${settings.port}) đã sao chép") },
                        )
                        ParamRow(
                            label = "IP host của máy (Host - App khác của điện thoại kết nối)",
                            value = "127.0.0.1",
                            highlight = true,
                            onCopy = { copyToClipboard(context,  "127.0.0.1", "FTP URL",  "IP máy (127.0.0.1) đã sao chép") },
                        )
                        ParamRow(
                            label = "IP máy chủ mạng cục bộ (Host - máy tính/thiết bị ngoài kết nối)",
                            value = settings.connectionHost,
                            onCopy = { copyToClipboard(context,  settings.connectionHost, "FTP URL",  "IP mạng LAN (${settings.connectionHost}) đã sao chép") },
                        )
                        ParamRow(
                            label = "Tên đăng nhập (Username)",
                            value = settings.username,
                            onCopy = { copyToClipboard(context,  settings.username, "FTP URL",  "Tên người dùng (${settings.username}) đã sao chép") },
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Text(
                            text = "URL kết nối nhanh:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ParamRow(
                            label = "URL truy cập mạng LAN / máy tính",
                            value = settings.connectionUrl,
                            onCopy = { copyToClipboard(context,  settings.connectionUrl, "FTP URL",  "Đã sao chép địa chỉ FTP mạng LAN") },
                        )
                        ParamRow(
                            label = "App khác trên máy điện thoại truy cập URL",
                            value = settings.localUrl,
                            onCopy = { copyToClipboard(context,  settings.localUrl, "FTP URL",  "Địa chỉ FTP máy cục bộ đã được sao chép") },
                        )

                        Text(
                            text = "Gợi ý sử dụng: khi thêm FTP trong trình quản lý tệp bên thứ ba trên điện thoại (như MT Manager/Solid Explorer/CX File Explorer), máy chủ điền 127.0.0.1, cổng điền ${settings.port}; dưới cùng mạng Wi-Fi, máy tính kết nối máy chủ điền ${settings.connectionHost}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (vpnActive) {
                item {
                    NoticeBanner(
                        text = "Phát hiện điện thoại đang trong kết nối VPN, VPN có thể tiếp quản lưu lượng LAN khiến thiết bị bên ngoài không kết nối được. Nếu kết nối timeout hãy tạm dừng VPN trước hoặc thêm ứng dụng này vào danh sách trắng kết nối trực tiếp.",
                        isError = true,
                    )
                }
            }

            item {
                SectionLabel("Xác thực đăng nhập và quyền")
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Admin,
                        title = "Tên đăng nhập",
                        subtitle = "Tên người dùng dùng khi client FTP kết nối",
                        value = settings.username,
                        onClick = { showUsernameDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    // 文案必须与实际鉴权一致：FtpServiceManager 只采用 FTP 自己的密码，
                    // 不回落 SSH 密码（否则「留空即可」的提示会在配过 SSH 密码后变成 530）。
                    // 免密只发生在设为「允许匿名访问」时，故文案里必须点明这一点。
                    val passwordSubtitle = if (settings.passwordConfigured) {
                        "Mật khẩu đã cấu hình riêng và lưu mã hóa, nhấn để sửa hoặc xóa"
                    } else if (settings.anonymousEnabled) {
                        "Chưa đặt mật khẩu; đã bật truy cập ẩn danh, không cần mật khẩu vẫn đăng nhập được"
                    } else {
                        "Chưa đặt mật khẩu và chưa bật truy cập ẩn danh, máy khách bên ngoài sẽ không thể đăng nhập; nhấn vào đây để đặt mật khẩu"
                    }
                    val passwordValue = if (settings.passwordConfigured) {
                        "Đã đặt"
                    } else if (settings.anonymousEnabled) {
                        "Miễn mật khẩu (ẩn danh)"
                    } else {
                        "Chưa thiết lập"
                    }
                    SettingsRow(
                        icon = RuntimeIconName.Key,
                        title = "Mật khẩu đăng nhập",
                        subtitle = passwordSubtitle,
                        value = passwordValue,
                        onClick = { showPasswordDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Shield,
                        title = "Cho phép truy cập ẩn danh",
                        subtitle = if (settings.anonymousEnabled) "Client bên ngoài có thể đăng nhập mà không cần tên người dùng/mật khẩu" else "Yêu cầu xác thực nhập tên người dùng và mật khẩu",
                        trailing = {
                            RuntimeSwitch(
                                checked = settings.anonymousEnabled,
                                onCheckedChange = viewModel::toggleAnonymous,
                                enabled = !busy,
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Storage,
                        title = "Chế độ bảo vệ chỉ đọc",
                        subtitle = if (settings.readOnly) "Chế độ chỉ đọc có hiệu lực: cấm ghi, sửa, đổi tên hoặc xóa tệp từ bên ngoài" else "Chế độ đọc ghi: cho phép bên ngoài tự do upload, đổi tên và xóa tệp",
                        trailing = {
                            RuntimeSwitch(
                                checked = settings.readOnly,
                                onCheckedChange = viewModel::toggleReadOnly,
                                enabled = !busy,
                            )
                        },
                    )
                }
            }

            item {
                SectionLabel("Cấu hình mạng")
                SettingsGroup {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Network, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                        val portValue = portText.toIntOrNull()
                        val portValid = portValue != null && portValue in 1024..65535
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                            label = { Text("Cổng FTP") },
                            isError = portText.isNotBlank() && !portValid,
                            supportingText = {
                                Text(
                                    if (portText.isNotBlank() && !portValid) "Cổng phải nằm trong khoảng 1024–65535"
                                    else "Cổng mặc định: 2121 (phạm vi 1024–65535)",
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        RuntimeOutlinedButton(
                            onClick = { copyToClipboard(context,  settings.port.toString(), "FTP URL",  "Cổng FTP (${settings.port}) đã sao chép") },
                        ) {
                            RuntimeIcon(RuntimeIconName.Copy, Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("复制")
                        }
                        RuntimeOutlinedButton(
                            onClick = { viewModel.savePort(portText) },
                            enabled = !busy && portValid && portText != settings.port.toString(),
                        ) {
                            Text("保存")
                        }
                    }
                }
            }

            if (logs.isNotEmpty()) {
                item {
                    SectionLabel("Log truy cập gần đây")
                    RuntimeCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ) {
                        Text(
                            logs.takeLast(12).joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}


@Composable
private fun FtpPasswordDialog(
    passwordConfigured: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var password by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var confirmation by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val matches = password == confirmation
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (passwordConfigured) "Sửa mật khẩu đăng nhập FTP" else "Đặt mật khẩu đăng nhập FTP") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Mật khẩu sẽ được mã hóa lưu qua Android Keystore, client bên ngoài khi kết nối cần nhập mật khẩu này.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text("Mật khẩu mới") },
                    supportingText = { Text("Ít nhất 4 ký tự") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(128) },
                    label = { Text("Nhập lại mật khẩu") },
                    isError = confirmation.isNotEmpty() && !matches,
                    supportingText = {
                        if (confirmation.isNotEmpty() && !matches) Text("Mật khẩu nhập hai lần không khớp")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            RuntimeButton(
                onClick = { onSave(password) },
                enabled = password.length >= 4 && matches,
            ) { Text("保存") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (passwordConfigured) {
                    RuntimeTextButton(onClick = onClear) { Text("Xóa mật khẩu") }
                }
                RuntimeTextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun FtpUsernameDialog(
    currentUsername: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var username by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(currentUsername) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sửa tên người dùng đăng nhập FTP") },
        text = {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(32) },
                label = { Text("Tên đăng nhập") },
                supportingText = { Text("Mặc định là root") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            RuntimeButton(
                onClick = { onSave(username.trim().ifBlank { "root" }) },
                enabled = username.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            RuntimeTextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ParamRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        RuntimeOutlinedButton(
            onClick = onCopy,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            RuntimeIcon(RuntimeIconName.Copy, Modifier.size(13.dp))
            Spacer(Modifier.size(4.dp))
            Text("复制", style = MaterialTheme.typography.labelSmall)
        }
    }
}
