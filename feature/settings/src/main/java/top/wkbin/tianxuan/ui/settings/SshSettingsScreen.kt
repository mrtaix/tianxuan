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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
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
import top.wkbin.tianxuan.runtime.SshServiceState
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
fun SshSettingsScreen(
    onBack: () -> Unit,
    viewModel: SshSettingsViewModel = koinViewModel(),
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
    var authorizedKeysText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(settings.authorizedKeys) }
    var showPasswordDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.distroId, settings.port) { portText = settings.port.toString() }
    LaunchedEffect(settings.distroId, settings.authorizedKeys) { authorizedKeysText = settings.authorizedKeys }

    message?.let { current ->
        RuntimeAlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            title = { Text(if (messageIsError) "Thao tác SSH chưa hoàn thành" else "Truy cập từ xa SSH") },
            text = { Text(current) },
            confirmButton = { RuntimeTextButton(onClick = viewModel::consumeMessage) { Text("知道了") } },
        )
    }

    if (showPasswordDialog) {
        PasswordSettingsDialog(
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

    val statusText = when (serviceState) {
        is SshServiceState.Stopped -> "已停止"
        is SshServiceState.Installing -> "Đang cài đặt OpenSSH"
        is SshServiceState.Starting -> "Đang khởi động"
        is SshServiceState.Running -> "运行中"
        is SshServiceState.Failed -> "启动失败"
    }
    val busy = operating || serviceState is SshServiceState.Installing || serviceState is SshServiceState.Starting

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("Truy cập từ xa SSH", onBack, statusText = statusText) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NoticeBanner(
                    text = "SSH được cấu hình độc lập theo bản phân phối Linux hiện tại. Lần đầu bật sẽ cài OpenSSH theo nhu cầu; hỗ trợ đăng nhập root + mật khẩu truyền thống, cũng có thể dùng public key.",
                )
            }

            item {
                SectionLabel("Trạng thái dịch vụ")
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Server,
                        title = "Bật dịch vụ SSH",
                        subtitle = "Giữ bật và tự động khởi động cùng Linux runtime",
                        value = if (busy) {
                            when (serviceState) {
                                is SshServiceState.Installing -> "Đang cài đặt OpenSSH"
                                is SshServiceState.Starting -> "Đang chờ dịch vụ SSH sẵn sàng"
                                else -> "正在处理"
                            }
                        } else {
                            when (serviceState) {
                                is SshServiceState.Running -> "运行中"
                                is SshServiceState.Installing -> "安装中"
                                is SshServiceState.Starting -> "启动中"
                                is SshServiceState.Failed -> "失败"
                                is SshServiceState.Stopped -> if ((serviceState as SshServiceState.Stopped).installed) "Đã cài đặt" else "未安装"
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
                (serviceState as? SshServiceState.Failed)?.let { failed ->
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
                SectionLabel("Xác thực đăng nhập")
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Admin,
                        title = "Tên đăng nhập",
                        subtitle = "Tài khoản quản trị sandbox PRoot Linux",
                        value = "root",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Key,
                        title = "Mật khẩu đăng nhập",
                        subtitle = if (settings.passwordConfigured) "Mật khẩu đã lưu mã hóa, có thể nhấp để sửa hoặc xóa" else "Chưa đặt, bắt buộc cấu hình trước khi đăng nhập bằng mật khẩu",
                        value = if (settings.passwordConfigured) "Đã đặt" else "Chưa thiết lập",
                        onClick = { showPasswordDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Shield,
                        title = "Cho phép đăng nhập mật khẩu",
                        subtitle = if (settings.passwordAuthEnabled) "Khi kết nối nhập mật khẩu tương ứng của root" else "Xác thực mật khẩu đã tắt",
                        trailing = {
                            RuntimeSwitch(
                                checked = settings.passwordAuthEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && !settings.passwordConfigured) {
                                        showPasswordDialog = true
                                    } else {
                                        viewModel.setPasswordAuthEnabled(enabled)
                                    }
                                },
                                enabled = !busy,
                            )
                        },
                    )
                }
            }

            item {
                SectionLabel("Kết nối")
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = settings.connectionCommand,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Lần đầu kết nối nhập yes để xác nhận vân tay máy chủ, sau đó nhập mật khẩu đăng nhập root",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RuntimeOutlinedButton(
                            onClick = { copyToClipboard(context,  settings.connectionCommand, "SSH command",  "Đã sao chép lệnh SSH") },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("复制命令")
                        }
                    }
                }
            }

            if (vpnActive) {
                item {
                    NoticeBanner(
                        text = "Phát hiện điện thoại đang trong kết nối VPN, VPN hiện tại sẽ tiếp quản lưu lượng của ứng dụng này, máy tính trong cùng mạng LAN có thể bị timeout khi kết nối. Hãy tắt VPN trước, hoặc thêm ứng dụng này vào danh sách kết nối trực tiếp/loại trừ của VPN.",
                        isError = true,
                    )
                }
            }

            item {
                SectionLabel("Cấu hình mạng")
                SettingsGroup {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Network, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                        val portValue = portText.toIntOrNull()
                        val portValid = portValue != null && portValue in 1024..65535
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                            label = { Text("Cổng SSH") },
                            isError = portText.isNotBlank() && !portValid,
                            supportingText = {
                                Text(
                                    if (portText.isNotBlank() && !portValid) "Cổng phải nằm trong khoảng 1024–65535"
                                    else "Phạm vi cho phép：1024–65535",
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        RuntimeOutlinedButton(onClick = { viewModel.savePort(portText) }, enabled = !busy && portValid) { Text("保存") }
                    }
                }
            }

            item {
                SectionLabel("Đăng nhập khóa công khai (tùy chọn)")
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Không cần public key cũng có thể đăng nhập bằng mật khẩu. Khi cần kết nối không mật khẩu, mỗi dòng thêm một public key OpenSSH.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = authorizedKeysText,
                            onValueChange = { authorizedKeysText = it },
                            label = { Text("authorized_keys") },
                            placeholder = { Text("ssh-ed25519 AAAAC3... user@device") },
                            minLines = 4,
                            maxLines = 8,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        RuntimeButton(
                            onClick = { viewModel.saveAuthorizedKeys(authorizedKeysText) },
                            enabled = !busy,
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Save, Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Lưu khóa công khai")
                        }
                    }
                }
            }

            if (logs.isNotEmpty()) {
                item {
                    SectionLabel("Log gần nhất")
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
private fun PasswordSettingsDialog(
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
        title = { Text(if (passwordConfigured) "Sửa mật khẩu đăng nhập SSH" else "Đặt mật khẩu đăng nhập SSH") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Mật khẩu sẽ được mã hóa lưu qua Android Keystore và áp dụng cho tài khoản root của bản phân phối Linux hiện tại.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text("Mật khẩu mới") },
                    supportingText = { Text("Ít nhất 8 ký tự, không được chứa dấu hai chấm") },
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
                enabled = password.length >= 8 && matches && ':' !in password,
            ) { Text("Lưu và bật") }
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
