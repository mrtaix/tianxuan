package top.wkbin.tianxuan.ui.iteration

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.ui.components.NoticeBanner
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.components.SectionHeader

@Composable
fun CustomIterationScreen(
    onBack: () -> Unit,
    onNavigateToChat: (prefillPrompt: String) -> Unit,
    viewModel: CustomIterationViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar(title = "Lặp tùy chỉnh (thử nghiệm)", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 头部 Hero 卡片
            RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tùy biến toàn diện TianXuanDev chuyên thuộc",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Thiên Huyền (TianXuan) là hạ tầng mã nguồn mở hướng tới xây dựng runtime Linux trên điện thoại và Agent tự chủ. Thông qua «Tùy biến lặp», bạn có thể gọi AI Thiên Huyền ngay trong lòng bàn tay để sửa đổi chính Thiên Huyền, tự động biên dịch ra APK TianXuanDev độc lập qua GitHub Actions trên đám mây, và cùng giao lưu đóng góp với cộng đồng lập trình viên.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }

            // 特性清单
            SectionHeader(title = "Ma trận năng lực lõi", subtitle = "Chuẩn bị một lần, cùng xây dựng liên tục")

            IterationFeatureItem(
                title = "Workspace lặp cách ly",
                description = "Đường dẫn độc lập ~/custom_tianxuan, mọi sửa đổi hoàn toàn cách ly vật lý với môi trường máy chủ.",
                badgeColor = MaterialTheme.colorScheme.primary
            )

            IterationFeatureItem(
                title = "Skill quy chuẩn Agent chuyên dụng",
                description = "Skill tianxuan-custom-iteration tích hợp, tuân thủ nghiêm ngặt đa module, Compose và quy tắc bảo mật.",
                badgeColor = MaterialTheme.colorScheme.secondary
            )

            IterationFeatureItem(
                title = "Build CI đám mây GitHub Actions",
                description = "Miễn phải biên dịch rườm rà trên điện thoại, tự động tạo APK độc lập TianXuanDev (top.wkbin.tianxuan.dev).",
                badgeColor = MaterialTheme.colorScheme.tertiary
            )

            IterationFeatureItem(
                title = "Hai gói cùng tồn tại và vòng khép kín PR mã nguồn mở",
                description = "Bản thử nghiệm và bản chính thức cùng tồn tại hoàn hảo, sau khi xác minh tính năng xong có thể gửi PR chuẩn bằng một chạm để tham gia xây dựng cộng đồng.",
                badgeColor = MaterialTheme.colorScheme.primary
            )

            // 错误提示：可关闭，不再常驻；重试走下方"Bật lặp tùy chỉnh"按钮
            if (uiState.errorMessage != null) {
                NoticeBanner(
                    text = uiState.errorMessage ?: "",
                    isError = true,
                    onDismiss = viewModel::dismissError
                )
            }

            Spacer(Modifier.height(8.dp))

            // 启动按钮
            RuntimeButton(
                onClick = {
                    viewModel.startCustomIteration { prompt ->
                        onNavigateToChat(prompt)
                    }
                },
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (uiState.isBusy) {
                    RuntimeCircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Đang chuẩn bị workspace lặp…")
                } else {
                    Text(
                        text = "Bật lặp tùy chỉnh",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IterationFeatureItem(
    title: String,
    description: String,
    badgeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
