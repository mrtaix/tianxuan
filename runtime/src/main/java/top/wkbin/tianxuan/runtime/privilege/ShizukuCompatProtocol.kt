package top.wkbin.tianxuan.runtime.privilege

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Private stdin/stdout protocol between the app and the compat runner. */
@Serializable
internal data class CompatRequest(
    val id: Long,
    val operation: String,
    val command: List<String> = emptyList(),
    val environment: List<String>? = null,
    val timeoutMs: Int = 30_000,
    val target: Long = -1,
)

@Serializable
internal data class CompatResponse(
    val id: Long,
    val result: String = "",
    val error: String? = null,
    val uid: Int = -1,
    val protocol: Int = 1,
)

internal object ShizukuCompatProtocol {
    val json = Json { ignoreUnknownKeys = false }
    const val MAIN_CLASS = "top.wkbin.tianxuan.runtime.privilege.ShizukuCompatMain"

    fun launchCommand(apkPath: String): Array<String> {
        val quoted = "'" + apkPath.replace("'", "'\\''") + "'"
        return arrayOf(
            "/system/bin/sh", "-c",
            "export CLASSPATH=$quoted; exec /system/bin/app_process /system/bin $MAIN_CLASS",
        )
    }
}
