package top.wkbin.tianxuan.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.wkbin.tianxuan.core.model.QuickPhrase

/** Stable persistence ports consumed by feature, harness, and runtime layers. */
interface AiModelRepository {
    fun observeAll(): Flow<List<AiModelEntity>>
    suspend fun findById(id: String): AiModelEntity?
    suspend fun activeModel(): AiModelEntity?
    suspend fun upsert(model: AiModelEntity)
    suspend fun clearActive()
    suspend fun setActive(id: String)
    suspend fun activate(id: String) // 唯一激活切换：clearActive + setActive 必须单事务（转发 AiModelDao.activate）
    suspend fun updateReasoning(id: String, mode: String?, effort: String?)
    suspend fun delete(id: String)
}

interface HarnessSessionRepository {
    fun observeAll(): Flow<List<HarnessSessionEntity>>
    suspend fun findById(id: String): HarnessSessionEntity?
    suspend fun upsert(session: HarnessSessionEntity)
    suspend fun touch(id: String, updatedAt: Long)
    suspend fun rename(id: String, title: String, updatedAt: Long)
    suspend fun setApprovalMode(id: String, approvalMode: String, updatedAt: Long)
    suspend fun setApprovalModeForAll(approvalMode: String, updatedAt: Long)
    suspend fun setRunMode(id: String, runMode: String, updatedAt: Long)
    suspend fun setModelSelection(id: String, modelId: String?, modelVariant: String?, updatedAt: Long)
    suspend fun deleteSession(id: String)
    suspend fun countInRange(start: Long?, end: Long?): Int
    suspend fun listAll(): List<HarnessSessionEntity>
}

interface WorkspaceRepository {
    fun observeAll(): Flow<List<WorkspaceEntity>>
    suspend fun listAll(): List<WorkspaceEntity>
    suspend fun findByName(name: String): WorkspaceEntity?
    suspend fun upsert(workspace: WorkspaceEntity)
    suspend fun delete(name: String)
}

interface TerminalSessionRepository {
    fun observeAll(): Flow<List<TerminalSessionEntity>>
    suspend fun listAll(): List<TerminalSessionEntity>
    suspend fun nextOrder(): Int
    suspend fun upsert(session: TerminalSessionEntity)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}

/** Persistence boundary for the privileged Android application inventory. */
interface AndroidAppRepository {
    fun observeAll(): Flow<List<AndroidAppEntity>>
    suspend fun findByPackageName(packageName: String): AndroidAppEntity?
    suspend fun search(query: String, limit: Int = 100): List<AndroidAppEntity>
    suspend fun count(): Int
    suspend fun reconcile(apps: List<AndroidAppEntity>)
}

interface BuildScriptRepository {
    fun observeScripts(): Flow<List<BuildScriptEntity>>
    fun observeBindings(): Flow<List<ProjectBuildScriptBindingEntity>>
    suspend fun listScripts(): List<BuildScriptEntity>
    suspend fun findScript(id: String): BuildScriptEntity?
    suspend fun findBinding(projectName: String): ProjectBuildScriptBindingEntity?
    suspend fun resolvedScript(projectName: String): BuildScriptEntity?
    suspend fun upsertScript(script: BuildScriptEntity)
    suspend fun deleteScript(id: String): Boolean
    suspend fun bind(projectName: String, scriptId: String)
    suspend fun unbind(projectName: String)
    suspend fun ensureBuiltinScripts(androidScript: String = "", flutterScript: String = "")
}

class RoomBuildScriptRepository(
    private val dao: BuildScriptDao,
) : BuildScriptRepository {
    override fun observeScripts() = dao.observeScripts()
    override fun observeBindings() = dao.observeBindings()
    override suspend fun listScripts(): List<BuildScriptEntity> {
        val scripts = dao.listScripts()
        if (scripts.isEmpty()) {
            ensureBuiltinScripts()
            return dao.listScripts()
        }
        return scripts
    }
    override suspend fun findScript(id: String): BuildScriptEntity? {
        val script = dao.findScript(id)
        if (script == null && (id == "builtin-android" || id == "builtin-flutter")) {
            ensureBuiltinScripts()
            return dao.findScript(id)
        }
        return script
    }
    override suspend fun findBinding(projectName: String) = dao.findBinding(projectName)
    override suspend fun resolvedScript(projectName: String): BuildScriptEntity? = dao.findBinding(projectName)?.let { findScript(it.scriptId) }
    override suspend fun upsertScript(script: BuildScriptEntity) = dao.upsertScript(script)
    override suspend fun deleteScript(id: String) = dao.deleteScriptAndBindings(id)
    override suspend fun bind(projectName: String, scriptId: String) {
        requireNotNull(findScript(scriptId)) { "Script build không tồn tại：$scriptId" }
        dao.upsertBinding(ProjectBuildScriptBindingEntity(projectName, scriptId, System.currentTimeMillis()))
    }
    override suspend fun unbind(projectName: String) = dao.deleteBinding(projectName)

    override suspend fun ensureBuiltinScripts(androidScript: String, flutterScript: String) {
        val now = System.currentTimeMillis()
        val existing = dao.listScripts().associateBy { it.id }
        val builtins = listOf(
            BuildScriptEntity(
                id = "builtin-android",
                name = "Android chuẩn",
                description = "Thien Huyen tích hợp script build Android, có thể sao chép rồi thích ứng cho phụ thuộc cũ hoặc mới.",
                projectType = "ANDROID",
                content = androidScript.ifBlank { DEFAULT_BUILTIN_ANDROID_SCRIPT },
                isBuiltin = true,
                createdAt = now,
                updatedAt = now,
            ),
            BuildScriptEntity(
                id = "builtin-flutter",
                name = "Flutter tiêu chuẩn",
                description = "Thien Huyen tích hợp script build Flutter APK, có thể sao chép rồi tùy biến.",
                projectType = "FLUTTER",
                content = flutterScript.ifBlank { DEFAULT_BUILTIN_FLUTTER_SCRIPT },
                isBuiltin = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        builtins.forEach { builtin ->
            val old = existing[builtin.id]
            if (old == null) {
                dao.upsertScript(builtin)
            } else if (old.isBuiltin && isLegacyBrokenStub(old.content)) {
                dao.upsertScript(old.copy(content = builtin.content, updatedAt = now))
            }
        }
    }

    private fun isLegacyBrokenStub(content: String): Boolean =
        content.contains("elif [ -x /opt/tianxuan/bin/gradle ]; then") ||
            (content.contains("TARGET=\"\${2:-apk --debug}\"") && content.contains("flutter pub get"))

    companion object {
        const val DEFAULT_BUILTIN_ANDROID_SCRIPT =
            "#!/bin/sh\n# Kịch bản build Android chuẩn Thiên Huyền\n# Mặc định điều phối /opt/tianxuan/scripts/tianxuan-build.sh để nạp môi trường đầy đủ, căn chỉnh kiến trúc AAPT2 và biên dịch Gradle\nset -eu\n\nPROJECT_DIR=\"\${1:-.}\"\nTASK=\"\${2:-assembleDebug}\"\n\nexec /bin/sh /opt/tianxuan/scripts/tianxuan-build.sh android \"\$PROJECT_DIR\" \"\$TASK\"\n"

        const val DEFAULT_BUILTIN_FLUTTER_SCRIPT =
            "#!/bin/sh\n# Điểm vào script xây dựng Flutter chuẩn Thiên Huyền\n# Mặc định điều phối /opt/tianxuan/scripts/tianxuan-build.sh thực thi kiểm tra toàn bộ chuỗi công cụ đa nền và xây dựng Flutter APK\nset -eu\n\nPROJECT_DIR=\"\${1:-.}\"\nTARGET=\"\${2:-apk --debug --target-platform android-arm64}\"\n\nexec /bin/sh /opt/tianxuan/scripts/tianxuan-build.sh flutter \"\$PROJECT_DIR\" \$TARGET\n"
    }
}

class RoomAndroidAppRepository(private val dao: AndroidAppDao) : AndroidAppRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun findByPackageName(packageName: String) = dao.findByPackageName(packageName)
    override suspend fun search(query: String, limit: Int) = dao.search(query, limit)
    override suspend fun count() = dao.count()
    override suspend fun reconcile(apps: List<AndroidAppEntity>) = dao.reconcile(apps)
}

interface AgentContextRepository {
    suspend fun saveMemory(memory: AgentMemoryEntity)
    suspend fun getMemoryById(id: String): AgentMemoryEntity?
    suspend fun getMemoryByKey(key: String, scope: String, ownerId: String): AgentMemoryEntity?
    suspend fun getMemoryBySubjectKey(subjectKey: String, scope: String, ownerId: String): AgentMemoryEntity?
    suspend fun getMemoriesForContext(projectOwnerId: String, sessionId: String, limit: Int = 100): List<AgentMemoryEntity>
    suspend fun countMemories(scope: String, ownerId: String): Int
    fun observeAllMemories(): Flow<List<AgentMemoryEntity>>
    suspend fun searchMemories(query: String, projectOwnerId: String, sessionId: String, limit: Int = 50): List<AgentMemoryEntity>
    suspend fun getPinnedMemories(projectOwnerId: String, sessionId: String): List<AgentMemoryEntity>
    suspend fun getFreshMemories(projectOwnerId: String, sessionId: String, pinned: Boolean, now: Long, limit: Int = 100): List<AgentMemoryEntity>
    suspend fun touchMemory(id: String, now: Long)
    suspend fun deleteMemoryById(id: String)
    suspend fun deleteMemoryByKey(key: String, scope: String, ownerId: String)
    suspend fun savePlan(plan: AgentPlanEntity)
    suspend fun getPlanBySession(sessionId: String): AgentPlanEntity?
    suspend fun getActivePlan(sessionId: String): AgentPlanEntity?
    suspend fun deletePlanBySession(sessionId: String)
    suspend fun saveScratchpad(scratchpad: AgentScratchpadEntity)
    suspend fun getScratchpad(sessionId: String, key: String): AgentScratchpadEntity?
    suspend fun listScratchpads(sessionId: String): List<AgentScratchpadEntity>
    suspend fun deleteScratchpad(sessionId: String, key: String)
    suspend fun clearScratchpads(sessionId: String)
}

class RoomAiModelRepository(private val dao: AiModelDao) : AiModelRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun findById(id: String) = dao.findById(id)
    override suspend fun activeModel() = dao.activeModel()
    override suspend fun upsert(model: AiModelEntity) = dao.upsert(model)
    override suspend fun clearActive() = dao.clearActive()
    override suspend fun setActive(id: String) = dao.setActive(id)
    override suspend fun activate(id: String) = dao.activate(id)
    override suspend fun updateReasoning(id: String, mode: String?, effort: String?) = dao.updateReasoning(id, mode, effort)
    override suspend fun delete(id: String) = dao.delete(id)
}

class RoomHarnessSessionRepository(private val dao: HarnessSessionDao) : HarnessSessionRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun findById(id: String) = dao.findById(id)
    override suspend fun upsert(session: HarnessSessionEntity) = dao.upsert(session)
    override suspend fun touch(id: String, updatedAt: Long) = dao.touch(id, updatedAt)
    override suspend fun rename(id: String, title: String, updatedAt: Long) = dao.rename(id, title, updatedAt)
    override suspend fun setApprovalMode(id: String, approvalMode: String, updatedAt: Long) = dao.setApprovalMode(id, approvalMode, updatedAt)
    override suspend fun setApprovalModeForAll(approvalMode: String, updatedAt: Long) = dao.setApprovalModeForAll(approvalMode, updatedAt)
    override suspend fun setRunMode(id: String, runMode: String, updatedAt: Long) = dao.setRunMode(id, runMode, updatedAt)
    override suspend fun setModelSelection(id: String, modelId: String?, modelVariant: String?, updatedAt: Long) =
        dao.setModelSelection(id, modelId, modelVariant, updatedAt)
    override suspend fun deleteSession(id: String) = dao.deleteSession(id)
    override suspend fun countInRange(start: Long?, end: Long?) = dao.countInRange(start, end)
    override suspend fun listAll() = dao.listAll()
}

class RoomWorkspaceRepository(private val dao: WorkspaceDao) : WorkspaceRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun listAll() = dao.listAll()
    override suspend fun findByName(name: String) = dao.findByName(name)
    override suspend fun upsert(workspace: WorkspaceEntity) = dao.upsert(workspace)
    override suspend fun delete(name: String) = dao.delete(name)
}

class RoomTerminalSessionRepository(private val dao: TerminalSessionDao) : TerminalSessionRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun listAll() = dao.listAll()
    override suspend fun nextOrder() = dao.nextOrder()
    override suspend fun upsert(session: TerminalSessionEntity) = dao.upsert(session)
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomAgentContextRepository(private val dao: AgentContextDao) : AgentContextRepository {
    override suspend fun saveMemory(memory: AgentMemoryEntity) = dao.saveMemory(memory)
    override suspend fun getMemoryById(id: String) = dao.getMemoryById(id)
    override suspend fun getMemoryByKey(key: String, scope: String, ownerId: String) = dao.getMemoryByKey(key, scope, ownerId)
    override suspend fun getMemoryBySubjectKey(subjectKey: String, scope: String, ownerId: String) =
        dao.getMemoryBySubjectKey(subjectKey, scope, ownerId)
    override suspend fun getMemoriesForContext(projectOwnerId: String, sessionId: String, limit: Int) =
        dao.getMemoriesForContext(projectOwnerId, sessionId, limit)
    override suspend fun countMemories(scope: String, ownerId: String) = dao.countMemories(scope, ownerId)
    override fun observeAllMemories() = dao.observeAllMemories()
    override suspend fun searchMemories(query: String, projectOwnerId: String, sessionId: String, limit: Int) =
        dao.searchMemories(query, projectOwnerId, sessionId, limit)
    override suspend fun getPinnedMemories(projectOwnerId: String, sessionId: String) =
        dao.getPinnedMemories(projectOwnerId, sessionId)
    override suspend fun getFreshMemories(projectOwnerId: String, sessionId: String, pinned: Boolean, now: Long, limit: Int) =
        dao.getFreshMemories(projectOwnerId, sessionId, pinned, now, limit)
    override suspend fun touchMemory(id: String, now: Long) = dao.touchMemory(id, now)
    override suspend fun deleteMemoryById(id: String) = dao.deleteMemoryById(id)
    override suspend fun deleteMemoryByKey(key: String, scope: String, ownerId: String) = dao.deleteMemoryByKey(key, scope, ownerId)
    override suspend fun savePlan(plan: AgentPlanEntity) = dao.savePlan(plan)
    override suspend fun getPlanBySession(sessionId: String) = dao.getPlanBySession(sessionId)
    override suspend fun getActivePlan(sessionId: String) = dao.getActivePlan(sessionId)
    override suspend fun deletePlanBySession(sessionId: String) = dao.deletePlanBySession(sessionId)
    override suspend fun saveScratchpad(scratchpad: AgentScratchpadEntity) = dao.saveScratchpad(scratchpad)
    override suspend fun getScratchpad(sessionId: String, key: String) = dao.getScratchpad(sessionId, key)
    override suspend fun listScratchpads(sessionId: String) = dao.listScratchpads(sessionId)
    override suspend fun deleteScratchpad(sessionId: String, key: String) = dao.deleteScratchpad(sessionId, key)
    override suspend fun clearScratchpads(sessionId: String) = dao.clearScratchpads(sessionId)
}

interface QuickPhraseRepository {
    fun observeAll(): Flow<List<QuickPhrase>>
    suspend fun getAll(): List<QuickPhrase>
    suspend fun findById(id: String): QuickPhrase?
    suspend fun upsert(phrase: QuickPhrase)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun delete(id: String)
    suspend fun resetToDefault()
    suspend fun ensureInitialized()
}

class RoomQuickPhraseRepository(
    private val dao: QuickPhraseDao,
) : QuickPhraseRepository {
    override fun observeAll(): Flow<List<QuickPhrase>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<QuickPhrase> =
        dao.getAll().map { it.toDomain() }

    override suspend fun findById(id: String): QuickPhrase? =
        dao.findById(id)?.toDomain()

    override suspend fun upsert(phrase: QuickPhrase) =
        dao.upsert(QuickPhraseEntity.fromDomain(phrase))

    override suspend fun setEnabled(id: String, enabled: Boolean) =
        dao.setEnabled(id, enabled)

    override suspend fun delete(id: String) =
        dao.delete(id)

    override suspend fun resetToDefault() {
        // 单事务重置：clearAll 与 upsertAll 分离时，崩溃窗口内自建短语已删、默认未写入
        dao.resetToDefault(defaultQuickPhrases.map { QuickPhraseEntity.fromDomain(it) })
    }

    override suspend fun ensureInitialized() {
        if (dao.count() == 0) {
            dao.upsertAll(defaultQuickPhrases.map { QuickPhraseEntity.fromDomain(it) })
        }
    }

    companion object {
        val defaultQuickPhrases = listOf(
            QuickPhrase(
                id = "builtin_run",
                title = "Chạy mã",
                content = "/run ",
                description = "Thực thi mã điểm vào của workspace hiện tại (như python main.py / npm start)",
                iconName = "Play",
                targetProjectType = null,
                isEnabled = true,
                sortOrder = 1,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_install",
                title = "Cài đặt phụ thuộc",
                content = "/install ",
                description = "Cài đặt phụ thuộc hệ thống hoặc ngôn ngữ trong sandbox Linux (apt / pip / npm)",
                iconName = "Package",
                targetProjectType = null,
                isEnabled = true,
                sortOrder = 2,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_init",
                title = "Khởi tạo dự án",
                content = "/init ",
                description = "Tạo mẫu khung dự án mới (Python, Node.js, C/C++, HTML)",
                iconName = "Plus",
                targetProjectType = null,
                isEnabled = true,
                sortOrder = 3,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_git",
                title = "Trạng thái Git",
                content = "/git status",
                description = "Xem trạng thái, commit hoặc pull kho quản lý phiên bản",
                iconName = "Code",
                targetProjectType = null,
                isEnabled = true,
                sortOrder = 4,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_test",
                title = "Chạy test",
                content = "/test ",
                description = "Thực thi unit test và xác minh mã",
                iconName = "Check",
                targetProjectType = null,
                isEnabled = true,
                sortOrder = 5,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_help",
                title = "Môi trường và trợ giúp",
                content = "/help",
                description = "Xem môi trường sandbox PRoot Linux hiện tại và mô tả công cụ Agent",
                iconName = "Alert",
                targetProjectType = null,
                isEnabled = true,
                sortOrder = 6,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_android_check",
                title = "Kiểm tra dự án Android",
                content = "Vui lòng kiểm tra cấu trúc dự án Android hiện tại, cấu hình Gradle, Manifest, tên gói và môi trường build; phát hiện vấn đề thì trực tiếp sửa tệp và xác minh.",
                description = "Kiểm tra Gradle, Manifest, tên gói và môi trường build hiện tại",
                iconName = "Check",
                targetProjectType = "ANDROID",
                isEnabled = true,
                sortOrder = 10,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_android_build",
                title = "Biên dịch và cài đặt lên điện thoại",
                content = "Hãy build dự án Android hiện tại, sau khi thành công thì export APK ra điện thoại và gọi trình cài đặt; ưu tiên dùng script build của workspace hiện tại và tianxuan-host install-apk.",
                description = "Xây dựng Debug APK, xuất và gọi trình cài đặt điện thoại",
                iconName = "Play",
                targetProjectType = "ANDROID",
                isEnabled = true,
                sortOrder = 11,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_android_debug",
                title = "Điều tra xây dựng Android",
                content = "Hãy đọc log build Android gần nhất, định vị lỗi thật và trực tiếp sửa script hoặc tệp dự án, rồi xác minh lại.",
                description = "Định vị vấn đề Gradle, Kotlin, AAPT2 hoặc cài đặt",
                iconName = "Alert",
                targetProjectType = "ANDROID",
                isEnabled = true,
                sortOrder = 12,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_flutter_check",
                title = "Kiểm tra dự án Flutter",
                content = "Hãy kiểm tra pubspec.yaml, điểm vào Dart và cấu hình Android Gradle của dự án Flutter hiện tại, phát hiện vấn đề thì sửa trực tiếp và xác minh.",
                description = "Kiểm tra pubspec, entry Dart và cấu hình host Android",
                iconName = "Check",
                targetProjectType = "FLUTTER",
                isEnabled = true,
                sortOrder = 20,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_flutter_build",
                title = "Biên dịch và cài đặt Flutter",
                content = "Vui lòng thực hiện kiểm tra phụ thuộc Flutter và xây dựng Debug APK, sau khi thành công xuất APK ra điện thoại và gọi tianxuan-host install-apk.",
                description = "Kéo phụ thuộc, build APK và gọi trình cài đặt",
                iconName = "Play",
                targetProjectType = "FLUTTER",
                isEnabled = true,
                sortOrder = 21,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_flutter_debug",
                title = "Khắc phục sự cố build Flutter",
                content = "Vui lòng đọc lỗi xây dựng Flutter gần nhất, định vị căn nguyên phụ thuộc, Gradle hoặc AAPT2, sửa trực tiếp dự án và xác minh lại.",
                description = "Định vị lỗi phụ thuộc, Gradle hoặc AAPT2",
                iconName = "Alert",
                targetProjectType = "FLUTTER",
                isEnabled = true,
                sortOrder = 22,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_reverse_analyze",
                title = "Phân tích dự án APK",
                content = "Hãy đọc apk-info.properties và REVERSE.md của dự án dịch ngược hiện tại, dùng jadx/apktool phân tích APK và báo cáo các phát hiện then chốt.",
                description = "Đọc manifest, DEX, tài nguyên và đặc trưng gia cố",
                iconName = "Search",
                targetProjectType = "REVERSE",
                isEnabled = true,
                sortOrder = 30,
                isBuiltin = true,
            ),
            QuickPhrase(
                id = "builtin_reverse_decode",
                title = "Giải nén và dịch ngược",
                content = "Hãy thực hiện giải gói và dịch ngược an toàn APK gốc trong dự án hiện tại, giữ lại tệp gốc và ghi sản phẩm vào thư mục đầu ra mới.",
                description = "Thực thi quy trình giải nén JADX hoặc apktool",
                iconName = "Code",
                targetProjectType = "REVERSE",
                isEnabled = true,
                sortOrder = 31,
                isBuiltin = true,
            ),
        )
    }
}
