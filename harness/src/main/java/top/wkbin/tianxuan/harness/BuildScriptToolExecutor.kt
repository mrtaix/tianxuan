package top.wkbin.tianxuan.harness

import java.io.File
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.tianxuan.core.database.BuildScriptEntity
import top.wkbin.tianxuan.core.database.BuildScriptRepository

/** Controlled Harness API for reusable workshop scripts. */
class BuildScriptToolExecutor(
    private val repository: BuildScriptRepository,
) {
    suspend fun execute(args: JsonObject, workspace: String): Pair<Boolean, String> {
        val action = args.string("action").trim().lowercase()
        return when (action) {
            "list" -> {
                val scripts = repository.listScripts()
                true to if (scripts.isEmpty()) "Hiện chưa có script xây dựng" else scripts.joinToString("\n") {
                    "${it.id}\t[${it.projectType}]\t${it.name}\t${it.description.ifBlank { "无描述" }}${if (it.isBuiltin) "\t[builtin]" else ""}"
                }
            }
            "get" -> {
                val id = args.string("id")
                if (id.isBlank()) return false to "Thiếu tham số: id"
                val script = repository.findScript(id) ?: return false to "Script xây dựng không tồn tại: $id"
                true to "id: ${script.id}\nname: ${script.name}\nproject_type: ${script.projectType}\ndescription: ${script.description}\n--- script ---\n${script.content}"
            }
            "create", "update" -> {
                val id = args.string("id")
                if (action == "update" && id.isBlank()) return false to "Thiếu tham số: id"
                val old = if (action == "update") repository.findScript(id) ?: return false to "Script xây dựng không tồn tại: $id" else null
                val name = args.string("name").ifBlank { old?.name.orEmpty() }
                val type = args.string("project_type").ifBlank { old?.projectType.orEmpty() }.trim().uppercase()
                val content = args.string("content").ifBlank { old?.content.orEmpty() }
                if (name.isBlank()) return false to "name không được rỗng"
                if (type !in setOf("ANDROID", "FLUTTER")) return false to "project_type chỉ có thể là android hoặc flutter"
                if (content.isBlank() || content.length > 200_000) return false to "content không được rỗng và không vượt quá 200 KB"
                if (!content.lineSequence().firstOrNull().orEmpty().contains("sh")) return false to "Dòng đầu script phải khai báo shell (ví dụ #!/bin/sh)"
                val now = System.currentTimeMillis()
                val saved = BuildScriptEntity(
                    id = old?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    description = args.string("description").ifBlank { old?.description.orEmpty() }.trim(),
                    projectType = type,
                    content = content.removePrefix("\uFEFF").replace("\r\n", "\n"),
                    isBuiltin = old?.isBuiltin ?: false,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                )
                repository.upsertScript(saved)
                true to "Đã${if (old == null) "创建" else "更新"}Script build: ${saved.name} (${saved.id})"
            }
            "delete" -> {
                val id = args.string("id")
                if (id.isBlank()) return false to "Thiếu tham số: id"
                if (repository.deleteScript(id)) true to "Đã xóa script build và gắn kết dự án của nó" else false to "Script tích hợp sẵn không thể xóa, hoặc script không tồn tại"
            }
            "bind" -> {
                val project = resolveProject(args, workspace)
                if (project.isBlank()) return false to "project không được rỗng, và phiên hiện tại không có workspace"
                val id = args.string("id")
                if (id.isBlank()) return false to "Thiếu tham số: id"
                runCatching {
                    repository.bind(project, id)
                }.fold(
                    onSuccess = { true to "Đã gắn dự án $project vào script build $id" },
                    onFailure = { false to (it.message ?: "Mount thất bại") },
                )
            }
            "unbind" -> {
                val project = resolveProject(args, workspace)
                if (project.isBlank()) return false to "project không được rỗng, và phiên hiện tại không có workspace"
                repository.unbind(project)
                true to "Dự án $project đã khôi phục quy trình build chuẩn"
            }
            else -> false to "action không xác định: $action"
        }
    }

    private fun resolveProject(args: JsonObject, workspace: String): String =
        args.string("project").ifBlank { workspace.trim().trimEnd('/').substringAfterLast('/') }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content.orEmpty()
}
