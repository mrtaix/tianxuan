package top.wkbin.tianxuan.ui.settings.stats

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.core.model.StatsDateRangePreset
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeFilterChip
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeLinearProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.settings.stats.widgets.StatsHeatmap
import top.wkbin.tianxuan.ui.settings.stats.widgets.StatsMetricGrid
import top.wkbin.tianxuan.ui.settings.stats.widgets.StatsRankSection
import top.wkbin.tianxuan.ui.settings.stats.widgets.StatsSectionCard
import top.wkbin.tianxuan.ui.settings.stats.widgets.StatsUsageChart

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "Thống kê dữ liệu và phân tích mức dùng",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 顶部时间范围筛选胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = listOf(
                    StatsDateRangePreset.ALL_TIME to "Toàn bộ thời gian",
                    StatsDateRangePreset.LAST_30_DAYS to "30 ngày gần đây",
                    StatsDateRangePreset.PREVIOUS_MONTH to "Tháng trước",
                    StatsDateRangePreset.PREVIOUS_QUARTER to "Quý trước",
                )

                for ((preset, label) in presets) {
                    val selected = uiState.range.preset == preset
                    RuntimeFilterChip(
                        selected = selected,
                        onClick = { viewModel.setDateRangePreset(preset) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                        },
                    )
                }
            }

            if (uiState.isLoading) {
                RuntimeLinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 加载失败：错误态 + 重试入口（复用模块内 RuntimeCard + 按钮模式）
            uiState.error?.let { errorMessage ->
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            RuntimeButton(
                                onClick = { viewModel.refresh() },
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }
            }

            // 加载完成但没有任何数据：给出空态而非整页空白
            if (uiState.error == null && !uiState.isLoading && uiState.snapshot?.heatmap.isNullOrEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Chưa có dữ liệu thống kê. Sau khi bắt đầu dùng hội thoại Agent và công cụ, nơi đây sẽ hiển thị mức độ hoạt động và lượng dùng Token.",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val snapshot = uiState.snapshot
            if (snapshot != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 1. 活跃度打卡热力图
                    item {
                        StatsSectionCard(title = "Heatmap điểm danh độ hoạt động") {
                            StatsHeatmap(days = snapshot.heatmap)
                        }
                    }

                    // 2. 核心指标概览 6 宫格
                    item {
                        StatsSectionCard(title = "Tổng quan chỉ số cốt lõi") {
                            StatsMetricGrid(summary = snapshot.summary)
                        }
                    }

                    // 3. Token 用量趋势图表
                    item {
                        StatsSectionCard(title = "Lượng dùng Token và xu hướng tiêu thụ") {
                            StatsUsageChart(days = snapshot.trend)
                        }
                    }

                    // 4. 模型使用排行
                    item {
                        StatsRankSection(
                            title = "Xếp hạng sử dụng model",
                            leftHeader = "模型名称",
                            rightHeader = "Số lần gọi",
                            items = snapshot.modelRank,
                            icon = RuntimeIconName.Brain,
                        )
                    }

                    // 5. 助手 / 工程工作区排行
                    item {
                        StatsRankSection(
                            title = "Xếp hạng trợ lý và kỹ thuật",
                            leftHeader = "Vùng làm việc / Trợ lý",
                            rightHeader = "Tần suất sử dụng",
                            items = snapshot.assistantRank,
                            icon = RuntimeIconName.Bot,
                        )
                    }

                    // 6. 热门话题排行
                    item {
                        StatsRankSection(
                            title = "Xếp hạng phiên chủ đề hot",
                            leftHeader = "Tiêu đề phiên",
                            rightHeader = "Số lượng tin nhắn",
                            items = snapshot.topicRank,
                            icon = RuntimeIconName.Chat,
                        )
                    }
                }
            }
        }
    }
}
