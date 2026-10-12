package top.wkbin.tianxuan.runtime.rootfs

import top.wkbin.tianxuan.runtime.ElfInspector
import java.io.File
import java.nio.file.Files

/** Validates the complete shell -> PT_INTERP -> dynamic-loader chain before PRoot is started. */
class RootfsValidator(
    private val elfInspector: ElfInspector,
) {
    fun validate(rootfs: File): RootfsValidation {
        require(rootfs.isDirectory) { "Thư mục RootFS không tồn tại" }
        require(
            File(rootfs, "etc/os-release").isFile ||
                File(rootfs, "usr/lib/os-release").isFile,
        ) { "RootFS thiếu os-release" }

        val bash = resolveFirstExecutable(rootfs, BASH_PATHS)
        val posixShell = resolveFirstExecutable(rootfs, POSIX_SHELL_PATHS)
            ?: throw IllegalArgumentException("RootFS thiếu /bin/sh khả dụng")
        val primary = bash ?: posixShell
        val primaryInfo = elfInspector.requireAarch64(primary.file)
        val interpreterPath = primaryInfo.interpreter
            ?.takeIf { it.startsWith('/') }
            ?: throw IllegalArgumentException("${primary.guestPath} thiếu đường dẫn trình thông dịch ELF hợp lệ")
        val interpreter = resolveGuestPath(rootfs, interpreterPath)
        require(interpreter.isFile) {
            "${primary.guestPath} không tồn tại trình thông dịch ELF: $interpreterPath"
        }
        elfInspector.requireAarch64(interpreter)

        if (bash != null) {
            val shInfo = elfInspector.requireAarch64(posixShell.file)
            require(shInfo.interpreter == interpreterPath) {
                "/bin/sh và Bash dùng trình thông dịch ELF khác nhau"
            }
        }

        return RootfsValidation(
            bashPath = primary.guestPath,
            posixShellPath = posixShell.guestPath,
            interpreterPath = interpreterPath,
        )
    }

    fun isValid(rootfs: File): Boolean = runCatching { validate(rootfs) }.isSuccess

    private fun resolveFirstExecutable(rootfs: File, paths: List<String>): ResolvedGuestFile? =
        paths.firstNotNullOfOrNull { guestPath ->
            runCatching { resolveGuestPath(rootfs, guestPath) }
                .getOrNull()
                ?.takeIf { it.isFile }
                ?.let { ResolvedGuestFile(guestPath, it) }
        }

    /** Resolve absolute and relative symlinks as guest paths, never as Android host paths. */
    private fun resolveGuestPath(rootfs: File, guestPath: String): File {
        require(guestPath.startsWith('/')) { "Đường dẫn Guest phải là đường dẫn tuyệt đối: $guestPath" }
        val root = rootfs.toPath().toAbsolutePath().normalize()
        val pending = ArrayDeque(
            guestPath.split('/').filter { it.isNotBlank() && it != "." },
        )
        var current = root
        var symlinkCount = 0
        while (pending.isNotEmpty()) {
            val part = pending.removeFirst()
            require(part != "..") { "Đường dẫn Guest không được vượt qua RootFS: $guestPath" }
            val candidate = current.resolve(part).normalize()
            require(candidate.startsWith(root)) { "Đường dẫn Guest vượt qua RootFS: $guestPath" }
            if (Files.isSymbolicLink(candidate)) {
                require(++symlinkCount <= MAX_SYMLINK_DEPTH) { "Liên kết tượng trưng quá sâu: $guestPath" }
                val link = Files.readSymbolicLink(candidate)
                val linkParts = link.toString().replace('\\', '/').split('/')
                    .filter { it.isNotBlank() && it != "." }
                current = if (link.isAbsolute || link.toString().startsWith('/')) {
                    root
                } else {
                    candidate.parent
                }
                for (index in linkParts.indices.reversed()) {
                    pending.addFirst(linkParts[index])
                }
            } else {
                current = candidate
            }
        }
        require(current.startsWith(root)) { "Đường dẫn Guest vượt qua RootFS: $guestPath" }
        return current.toFile()
    }

    data class RootfsValidation(
        val bashPath: String,
        val posixShellPath: String,
        val interpreterPath: String,
    )

    private data class ResolvedGuestFile(
        val guestPath: String,
        val file: File,
    )

    private companion object {
        const val MAX_SYMLINK_DEPTH = 32
        val BASH_PATHS = listOf("/bin/bash", "/usr/bin/bash")
        val POSIX_SHELL_PATHS = listOf("/bin/sh", "/usr/bin/sh")
    }
}
