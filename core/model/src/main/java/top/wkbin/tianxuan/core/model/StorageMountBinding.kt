package top.wkbin.tianxuan.core.model

import kotlinx.serialization.Serializable

/**
 * Gắn kết lưu trữ giữa máy chủ (Android) và sandbox (Linux PRoot)
 * Tương ứng lệnh PRoot: -b <hostPath>:<guestPath>
 */
@Serializable
data class StorageMountBinding(
    val id: String,
    val name: String,
    val hostPath: String,
    val guestPath: String,
    val enabled: Boolean = true,
    val isSystemDefault: Boolean = false,
) : java.io.Serializable {

    companion object {
        /** Các gốc gắn kết được phép trong container: guestPath của binding phải nằm trong một trong số này */
        val ALLOWED_GUEST_MOUNT_ROOTS = setOf("/mnt", "/sdcard")

        /** Gốc gắn kết được phép phía máy chủ (lưu trữ chia sẻ Android) */
        const val SHARED_STORAGE_ROOT = "/storage/emulated/0"

        /**
         * Chuẩn hoá đường dẫn container: gộp đoạn rỗng và ".", trả về đường dẫn chuẩn bắt đầu bằng / và không có / ở cuối.
         * Khi không phải đường dẫn tuyệt đối hoặc chứa ".." thì trả về null (coi là không hợp lệ, không đoán ý người dùng).
         */
        fun normalizeGuestPath(path: String): String? {
            if (!path.startsWith('/')) return null
            val segments = path.split('/').filter { it.isNotBlank() && it != "." }
            if (segments.any { it == ".." }) return null
            return "/${segments.joinToString("/")}".trimEnd('/').ifBlank { "/" }
        }

        /** Đường dẫn container sau chuẩn hoá có nằm trong gốc gắn kết được phép không */
        fun isGuestPathAllowed(guestPath: String): Boolean {
            val guest = normalizeGuestPath(guestPath) ?: return false
            return ALLOWED_GUEST_MOUNT_ROOTS.any { root ->
                guest == root || guest.startsWith("$root/")
            }
        }

        /** Đường dẫn máy chủ (sau canonical) có nằm trong gốc lưu trữ chia sẻ không */
        fun isHostPathAllowed(hostPath: String): Boolean {
            val sharedRoot = java.io.File(SHARED_STORAGE_ROOT).canonicalFile
            val host = java.io.File(hostPath).canonicalFile
            return host == sharedRoot ||
                host.absolutePath.startsWith(sharedRoot.absolutePath + java.io.File.separator)
        }

        /**
         * Trả về lỗi kiểm tra đầu tiên của binding (văn bản tiếng Việt dễ đọc), null nghĩa là hợp lệ.
         * Dùng chung cho kiểm tra an toàn trước khi dựng lệnh PRoot, lọc dữ liệu bẩn lúc chạy, lưu/hiển thị ở trang cài đặt,
         * đảm bảo quy tắc ở ba nơi không bao giờ lệch nhau.
         */
        fun validationError(binding: StorageMountBinding): String? {
            if (':' in binding.hostPath || '\u0000' in binding.hostPath) return "Đường dẫn gắn kết phía máy chủ chứa ký tự không hợp lệ"
            if (':' in binding.guestPath || '\u0000' in binding.guestPath) return "Đường dẫn gắn kết trong container chứa ký tự không hợp lệ"
            if (!binding.guestPath.startsWith('/')) return "Đường dẫn gắn kết trong container phải là đường dẫn tuyệt đối"
            val segments = binding.guestPath.split('/').filter { it.isNotBlank() && it != "." }
            if (segments.any { it == ".." }) return "Đường dẫn gắn kết trong container không được chứa .."
            if (!isGuestPathAllowed(binding.guestPath)) return "Gắn kết trong container chỉ được phép nằm trong /mnt hoặc /sdcard"
            if (!isHostPathAllowed(binding.hostPath)) return "Gắn kết phía máy chủ chỉ được phép nằm trong $SHARED_STORAGE_ROOT"
            return null
        }
    }
}
