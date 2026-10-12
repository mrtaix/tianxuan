package top.wkbin.tianxuan.core.network

import java.io.IOException

sealed class DownloadError(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    class Security(message: String) : DownloadError(message)
    class Http(val statusCode: Int, message: String) : DownloadError(message)
    class EmptyResponse : DownloadError("Máy chủ trả về nội dung rỗng")
    class ChecksumMismatch(
        val expected: String,
        val actual: String,
    ) : DownloadError("Kiểm tra SHA-256 thất bại: expected=$expected actual=$actual")
    class Storage(message: String, cause: Throwable? = null) : DownloadError(message, cause)
    class RetryExhausted(message: String, cause: Throwable? = null) : DownloadError(message, cause)
}
