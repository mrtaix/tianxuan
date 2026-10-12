package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

// 赞赏码与赞助邮箱
private const val SPONSOR_EMAIL = "wangkebin1997@gmail.com"
// 收款码已打包进 settings 模块 mipmap 资源（settings_qr_*），离线可用、不依赖外网
private val SPONSOR_ALIPAY_QR_RES = top.wkbin.tianxuan.feature.settings.R.mipmap.settings_qr_alipay
private val SPONSOR_WECHAT_QR_RES = top.wkbin.tianxuan.feature.settings.R.mipmap.settings_qr_wechat
private val SponsorAccent: Color = Color(0xFFFF4D6D)

/**
 * 二级子页 5：赞助支持（鸣谢名单来自 GitHub 上的 sponsors.json，进入页面时才拉取）
 */
@Composable
fun SponsorScreen(
    onBack: () -> Unit,
    viewModel: SponsorListViewModel = koinViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sponsorState by viewModel.state.collectAsStateWithLifecycle()
    var showAlipayQrDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showWeChatQrDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    if (showAlipayQrDialog) {
        SponsorQrDialog(
            title = "Mã ủng hộ Alipay",
            qrRes = SPONSOR_ALIPAY_QR_RES,
            accent = Color(0xFF1677FF),
            onDismiss = { showAlipayQrDialog = false },
        )
    }
    if (showWeChatQrDialog) {
        SponsorQrDialog(
            title = "Mã tặng thưởng WeChat",
            qrRes = SPONSOR_WECHAT_QR_RES,
            accent = SponsorAccent,
            onDismiss = { showWeChatQrDialog = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "Tài trợ hỗ trợ",
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::refresh, contentDescription = "Làm mới danh sách") {
                        RuntimeIcon(
                            RuntimeIconName.Refresh,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 顶部引言
            item {
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SponsorAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                RuntimeIcon(RuntimeIconName.Sponsor, Modifier.size(22.dp), tint = SponsorAccent)
                            }
                            Text(
                                "Cảm ơn bạn đã cân nhắc tài trợ Thiên Huyền",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            "Thien Huyen (TianXuan) hoàn toàn miễn phí và mã nguồn mở, do tác giả tự duy trì trong thời gian rảnh. Mỗi khoản tài trợ của bạn đều dùng để mua API Token và tiếp tục phát triển, giúp chúng tôi làm dự án tốt hơn.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 赞助方式
            item {
                Text(
                    text = "Cách tài trợ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Sponsor,
                        title = "Mã ủng hộ Alipay",
                        subtitle = "Alipay quét mã, tùy tâm ủng hộ",
                        onClick = { showAlipayQrDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Sponsor,
                        title = "Mã tặng thưởng WeChat",
                        subtitle = "Quét WeChat, tùy tâm ủng hộ",
                        onClick = { showWeChatQrDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Mail,
                        title = "Tài trợ Token",
                        subtitle = "Email: wangkebin1997@gmail.com · Gửi mail là được",
                        onClick = { sendSponsorEmail(context) },
                    )
                }
            }

            // 回馈与鸣谢
            item {
                Text(
                    text = "Phản hồi và tri ân",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    when (val s = sponsorState) {
                        SponsorListUiState.Loading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    "Đang tải danh sách tri ân…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is SponsorListUiState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Tải danh sách thất bại：${s.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = viewModel::refresh) {
                                    Text("重试")
                                }
                            }
                        }
                        is SponsorListUiState.Success -> {
                            SponsorKindGroup("Tài trợ vốn", s.entries.filter { it.kind == SponsorKind.Funding })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SponsorKindGroup("Tài trợ tài nguyên", s.entries.filter { it.kind == SponsorKind.Resource })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SponsorKindGroup("Người đóng góp", s.entries.filter { it.kind == SponsorKind.Contribution })
                        }
                    }
                }
            }
        }
    }
}

/** 将打包在 mipmap 中的赞赏码二维码保存至系统相册 Pictures/TianXuan */
private fun saveSponsorQrToGallery(context: Context, qrRes: Int, namePrefix: String): Boolean {
    val resolver = context.contentResolver
    val filename = "tianxuan-sponsor-$namePrefix-${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TianXuan")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        val bitmap = BitmapFactory.decodeResource(context.resources, qrRes)
            ?: error("Không thể giải mã tài nguyên mã ủng hộ")
        resolver.openOutputStream(target)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("Không thể ghi tệp mục tiêu")
        resolver.update(
            target,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
        true
    }.getOrElse {
        resolver.delete(target, null, null)
        false
    }
}

/** 赞赏码弹窗：展示打包在 mipmap 中的二维码图片 */
@Composable
private fun SponsorQrDialog(
    title: String,
    qrRes: Int,
    accent: Color,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Sponsor, Modifier.size(22.dp), tint = accent)
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = qrRes,
                        contentDescription = "Ảnh $title",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        error = {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RuntimeIcon(RuntimeIconName.Sponsor, Modifier.size(32.dp), tint = accent.copy(alpha = 0.6f))
                                Text(
                                    "Tải mã QR thất bại, hãy thử lại sau",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
                Text(
                    "Mở App tương ứng quét mã là có thể ủng hộ, cảm ơn đã hỗ trợ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prefix = if (title.contains("微信")) "wechat" else "alipay"
                    val success = saveSponsorQrToGallery(context, qrRes, prefix)
                    if (success) {
                        Toast.makeText(context, "Đã lưu mã QR vào album ảnh", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Lưu thất bại, vui lòng thử lại sau", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Download, Modifier.size(16.dp), tint = Color.White)
                    Text("Lưu vào album", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

/** 唤起邮件客户端撰写 Token 赞助邮件（正文预填模板）；无邮件客户端时复制邮箱兜底 */
private fun sendSponsorEmail(context: Context) {
    val body = buildString {
        appendLine("Xin chào, tôi muốn tài trợ Thiên Huyền một Token, ủng hộ bạn tiếp tục phát triển:")
        appendLine()
        appendLine("API Base URL：")
        appendLine("API Key：")
        appendLine("Tên model (tùy chọn):")
        appendLine()
        appendLine("Cảm ơn sự đóng góp của bạn!")
    }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SPONSOR_EMAIL")).apply {
        putExtra(Intent.EXTRA_SUBJECT, "Tài trợ Token Thiên Huyền")
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        copyToClipboard(context, SPONSOR_EMAIL, "Email tài trợ Thien Huyen", "Đã sao chép email tài trợ: $SPONSOR_EMAIL, hãy mở email để viết thư")
    }
}

/** 鸣谢分类块：标题 + 头像名单（FlowRow 胶囊），空列表时显示占位文案 */
@Composable
private fun SponsorKindGroup(
    title: String,
    entries: List<SponsorEntry>,
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entries.isEmpty()) {
            Text(
                text = "Chưa có, cảm ơn từng người bạn âm thầm ủng hộ",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entries.forEach { entry -> SponsorChip(entry) }
            }
        }
    }
}

/** 单个鸣谢胶囊：头像 + 名称（可选补充说明） */
@Composable
private fun SponsorChip(entry: SponsorEntry) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = entry.avatarUrl,
                    contentDescription = entry.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            RuntimeIcon(
                                RuntimeIconName.Sponsor,
                                Modifier.size(14.dp),
                                tint = SponsorAccent,
                            )
                        }
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.note != null) {
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
