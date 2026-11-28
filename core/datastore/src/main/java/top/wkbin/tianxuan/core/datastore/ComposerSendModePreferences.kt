package top.wkbin.tianxuan.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Chế độ gửi của ô soạn tin trong chat (STEER = can thiệp ngay, NEXT_RUN = chờ lượt sau).
 *
 * Trước đây chế độ này chỉ nằm trong bộ nhớ ViewModel nên mỗi lần mở lại ứng dụng đều quay về
 * NEXT_RUN, người dùng phải chọn lại. Lưu vào DataStore để giữ nguyên lựa chọn giữa các phiên.
 */
internal val composerSendModeKey = stringPreferencesKey("chat_composer_send_mode")

/** Mã chế độ gửi đã lưu; rỗng nghĩa là chưa từng chọn (dùng mặc định NEXT_RUN). */
internal val Context.composerSendModePreference: Flow<String>
    get() = settingsDataStore.data.map { it[composerSendModeKey].orEmpty() }

/** Ghi lại chế độ gửi đã chọn. */
internal suspend fun Context.setComposerSendModePreference(value: String) {
    settingsDataStore.edit { it[composerSendModeKey] = value }
}
