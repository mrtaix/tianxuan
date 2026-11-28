package top.wkbin.tianxuan.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Chế độ gửi tin nhắn đang chờ (STEER/FOLLOW_UP) trong lúc agent đang chạy.
 *
 * Tách thành tệp riêng vì SettingsDataStore.kt đã chạm trần kích thước của
 * architecture-policy.json (baseline chỉ cho giảm), nên preference mới không thêm vào đó.
 *
 * Hai chế độ:
 * - false (mặc định): giữ nguyên hành vi cũ — chỉ nạp tin chờ khi model đã trả lời xong
 *   toàn bộ lượt (không còn tool call), tức đợi đến khi mọi lệnh kết thúc.
 * - true: nạp tin chờ ngay sau khi một loạt lệnh (tool call) vừa thực thi xong, để người dùng
 *   không phải đợi hết cả tác vụ dài mới được bổ sung chỉ dẫn.
 */
internal val queueSendAfterToolBatchKey = booleanPreferencesKey("agent_queue_send_after_tool_batch")

/** true = gửi tin chờ ngay sau mỗi loạt lệnh; false = đợi hết tất cả (mặc định). */
internal val Context.queueSendAfterToolBatchPreference: Flow<Boolean>
    get() = settingsDataStore.data.map { it[queueSendAfterToolBatchKey] ?: false }

/** Ghi lại lựa chọn chế độ gửi tin chờ. */
internal suspend fun Context.setQueueSendAfterToolBatchPreference(value: Boolean) {
    settingsDataStore.edit { it[queueSendAfterToolBatchKey] = value }
}
