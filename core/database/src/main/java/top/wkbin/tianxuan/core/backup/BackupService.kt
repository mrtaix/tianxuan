package top.wkbin.tianxuan.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.wkbin.tianxuan.core.database.HarnessEntryEntity
import top.wkbin.tianxuan.core.database.HarnessRuntimeDao
import top.wkbin.tianxuan.core.database.HarnessSessionDao
import top.wkbin.tianxuan.core.database.HarnessSessionEntity

/**
 * Sao lưu và phục hồi hội thoại + cấu hình dưới dạng JSON.
 *
 * Phạm vi: chỉ dữ liệu người dùng tạo ra (phiên, tin nhắn/entry, và tùy chọn cài đặt đã chọn).
 * Không sao lưu khóa bí mật/credential để tránh rò rỉ khi chia sẻ tệp.
 */
@Serializable
data class BackupSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val workspace: String = "",
    val projectType: String = "",
    val approvalMode: String = "",
    val runMode: String = "",
    val modelId: String? = null,
    val modelVariant: String? = null,
)

@Serializable
data class BackupEntry(
    val id: String,
    val sessionId: String,
    val parentId: String? = null,
    val createdAt: Long,
    val entryType: String,
    val customType: String? = null,
    val payloadJson: String,
)

@Serializable
data class BackupPayload(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val appPackage: String = "com.ctharness.app",
    val sessions: List<BackupSession> = emptyList(),
    val entries: List<BackupEntry> = emptyList(),
    /** Cặp khóa–giá trị cài đặt dạng chuỗi (chỉ các khóa được phép sao lưu). */
    val settings: Map<String, String> = emptyMap(),
)

/** Kết quả phục hồi để UI báo lại chính xác cho người dùng. */
data class RestoreResult(
    val sessions: Int,
    val entries: Int,
    val settings: Int,
)

class BackupService(
    private val sessionDao: HarnessSessionDao,
    private val runtimeDao: HarnessRuntimeDao,
    private val json: Json,
) {
    companion object {
        const val MIME = "application/json"
        const val DEFAULT_FILE_NAME = "ctharness-backup.json"
        const val MAX_ENTRIES_PER_SESSION = 20_000
    }

    /** Xuất toàn bộ phiên + entry ra JSON. */
    suspend fun export(sessions: List<HarnessSessionEntity>? = null, settings: Map<String, String> = emptyMap()): String {
        val allSessions = sessions ?: sessionDao.listAll()
        val entries = buildList {
            allSessions.forEach { session ->
                runCatching { runtimeDao.listEntries(session.id) }
                    .getOrDefault(emptyList())
                    .take(MAX_ENTRIES_PER_SESSION)
                    .forEach { add(it.toBackup()) }
            }
        }
        val payload = BackupPayload(
            exportedAt = System.currentTimeMillis(),
            sessions = allSessions.map { it.toBackup() },
            entries = entries,
            settings = settings,
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    /**
     * Nhập từ JSON. Id trùng sẽ được ghi đè (khôi phục lại đúng trạng thái đã sao lưu),
     * không xóa bất kỳ phiên nào đang có.
     */
    suspend fun import(rawJson: String): Result<RestoreResult> = runCatching {
        val payload = json.decodeFromString(BackupPayload.serializer(), rawJson)
        require(payload.schemaVersion in 1..1) { "Phiên bản sao lưu không hỗ trợ: ${payload.schemaVersion}" }
        payload.sessions.forEach { sessionDao.upsert(it.toEntity()) }
        payload.entries.forEach { entry ->
            runCatching { runtimeDao.insertEntry(entry.toEntity()) }
        }
        RestoreResult(
            sessions = payload.sessions.size,
            entries = payload.entries.size,
            settings = payload.settings.size,
        )
    }

    private fun HarnessSessionEntity.toBackup() = BackupSession(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        workspace = workspace,
        projectType = projectType,
        approvalMode = approvalMode,
        runMode = runMode,
        modelId = modelId,
        modelVariant = modelVariant,
    )

    private fun BackupSession.toEntity() = HarnessSessionEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        modelId = modelId,
        modelVariant = modelVariant,
        workspace = workspace,
        projectType = projectType,
        approvalMode = approvalMode.ifBlank { "assisted" },
        runMode = runMode.ifBlank { "build" },
    )

    private fun HarnessEntryEntity.toBackup() = BackupEntry(
        id = id,
        sessionId = sessionId,
        parentId = parentId,
        createdAt = createdAt,
        entryType = entryType,
        customType = customType,
        payloadJson = payloadJson,
    )

    private fun BackupEntry.toEntity() = HarnessEntryEntity(
        id = id,
        sessionId = sessionId,
        parentId = parentId,
        createdAt = createdAt,
        entryType = entryType,
        customType = customType,
        payloadJson = payloadJson,
    )
}
