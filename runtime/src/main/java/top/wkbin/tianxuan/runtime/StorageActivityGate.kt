package top.wkbin.tianxuan.runtime

/** Commands may run concurrently; cleanup needs an idle runtime and prevents new launches. */
internal class StorageActivityGate {
    private val monitor = Any()
    private var active = 0
    private var cleaning = false

    suspend fun <T> activity(block: suspend () -> T): T {
        synchronized(monitor) {
            check(!cleaning) { "Đang dọn lưu trữ, hãy khởi động tác vụ lại sau" }
            active++
        }
        try { return block() } finally { synchronized(monitor) { active-- } }
    }

    suspend fun cleanup(block: suspend () -> Unit) {
        synchronized(monitor) {
            check(!cleaning && active == 0) { "Có lệnh hoặc build đang chạy, vui lòng kết thúc rồi dọn" }
            cleaning = true
        }
        try { block() } finally { synchronized(monitor) { cleaning = false } }
    }
}
