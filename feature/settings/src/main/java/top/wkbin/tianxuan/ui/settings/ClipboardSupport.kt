package top.wkbin.tianxuan.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * Sao chép văn bản vào bộ nhớ tạm và hiển thị thông báo ngắn.
 *
 * CcSwitchScreen, FtpSettingsScreen và SshSettingsScreen dùng chung hàm này
 * thay vì duy trì ba bản sao khác nhau. Nhãn bộ nhớ tạm thuộc ngữ cảnh của
 * nơi gọi, nên nơi gọi quyết định nhãn và thông báo thành công.
 *
 * @param label Nhãn giúp người dùng nhận biết nguồn nội dung trong bộ nhớ tạm.
 * @param toast Thông báo phù hợp với nội dung vừa sao chép, như thông tin xác thực hoặc địa chỉ.
 */
internal fun copyToClipboard(context: Context, text: String, label: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    // Báo rõ khi không truy cập được bộ nhớ tạm, tránh khiến người dùng tưởng đã sao chép.
    if (clipboard == null) {
        Toast.makeText(context, "Không thể truy cập bộ nhớ tạm của hệ thống", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}