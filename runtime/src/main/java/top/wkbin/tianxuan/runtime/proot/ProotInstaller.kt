package top.wkbin.tianxuan.runtime.proot

import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.common.result.AppError
import top.wkbin.tianxuan.core.common.result.AppResult
import top.wkbin.tianxuan.core.common.result.ErrorCode
import top.wkbin.tianxuan.runtime.ElfInspector
import top.wkbin.tianxuan.runtime.RuntimePathManager
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verifies the APK-bundled PRoot runtime.
 *
 * Android 10+ does not allow this target-SDK-34 app to execute code downloaded
 * into filesDir. Both the tracer and its external loader therefore have to be
 * packaged as extracted native libraries; the selected Linux RootFS is downloaded through OCI.
 */
class ProotInstaller(
    private val pathManager: RuntimePathManager,
    private val elfInspector: ElfInspector,
    private val logger: AppLogger,
) {
    suspend fun install(): AppResult<File> = withContext(Dispatchers.IO) {
        try {
            pathManager.ensureDirectories()
            val proot = pathManager.bundledProotFile
            val loader = pathManager.bundledProotLoaderFile
            require(proot.isFile && proot.length() > MIN_RUNTIME_COMPONENT_BYTES) {
                "APK thiếu chương trình chính ARM64 PRoot (libproot.so)"
            }
            require(
                loader.isFile &&
                    loader.length() > MIN_RUNTIME_COMPONENT_BYTES &&
                    loader.length() <= MAX_LOADER_BYTES,
            ) {
                "APK thiếu PRoot ARM64 loader (libproot-loader.so)." +
                    "PRoot bản Termux không có loader tích hợp, không thể chỉ sao chép chương trình chính proot."
            }
            require(loader.canExecute()) {
                "PRoot loader không thể thực thi; phải giải nén từ APK nativeLibraryDir, không được đặt trong filesDir"
            }
            elfInspector.requireAarch64(proot)
            elfInspector.requireAarch64(loader)
            validateExecutable(proot)
            logger.i("APK-bundled PRoot tracer and loader validated")
            AppResult.Success(proot)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            logger.e("PRoot runtime validation failed", throwable)
            AppResult.Failure(
                AppError(
                    code = ErrorCode.INSTALLATION_FAILED,
                    message = "Kiểm tra thành phần chạy PRoot thất bại: ${throwable.message ?: "未知错误"}",
                    cause = throwable,
                ),
            )
        }
    }

    private fun validateExecutable(file: File) {
        val process = try {
            ProcessBuilder(file.absolutePath, "--version")
                .apply {
                    environment().clear()
                    environment().putAll(pathManager.hostProcessEnvironment())
                }
                .redirectErrorStream(true)
                .start()
        } catch (throwable: Throwable) {
            throw IllegalStateException(
                "Không thể khởi động ${file.absolutePath}: ${throwable.message}",
                throwable,
            )
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        require(exitCode == 0) {
            "Chương trình chính PRoot thực thi thất bại (exit=$exitCode): ${output.trim().ifBlank { "无输出" }}"
        }
        require(output.contains(EXPECTED_PROOT_VERSION)) {
            "Phiên bản chương trình chính PRoot không khớp, kỳ vọng $EXPECTED_PROOT_VERSION"
        }
    }

    private companion object {
        const val MIN_RUNTIME_COMPONENT_BYTES = 4096L
        const val MAX_LOADER_BYTES = 4L * 1024L * 1024L
        const val EXPECTED_PROOT_VERSION = "5.1.107.92"
    }
}
