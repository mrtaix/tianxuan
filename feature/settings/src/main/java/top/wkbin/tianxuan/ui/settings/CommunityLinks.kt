package top.wkbin.tianxuan.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import top.wkbin.tianxuan.core.model.Community

/** Mở liên kết ngoài; nếu không có trình duyệt, sao chép liên kết vào bộ nhớ tạm. */
internal fun openBrowser(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        // Dùng ClipboardSupport chung, bao gồm xử lý khi bộ nhớ tạm không khả dụng.
        // Không khôi phục hàm mở rộng riêng từng bỏ qua lỗi mà không báo cho người dùng.
        copyToClipboard(context, url, "URL", "Đã sao chép liên kết: $url")
    }
}

/** Tham gia nhóm QQ; nếu chưa cài QQ, sao chép mã nhóm từ [Community]. */
internal fun joinQqGroup(context: Context) {
    val uri = Uri.parse(Community.QQ_GROUP_URI)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        copyToClipboard(
            context,
            Community.QQ_GROUP_ID,
            "Nhóm cộng đồng chính thức CTHarness",
            "Đã sao chép mã nhóm QQ: ${Community.QQ_GROUP_ID}. Mở QQ và tìm mã này để tham gia.",
        )
    }
}