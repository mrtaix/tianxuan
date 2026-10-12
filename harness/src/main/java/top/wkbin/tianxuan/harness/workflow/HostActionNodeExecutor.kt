package top.wkbin.tianxuan.harness.workflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.wkbin.tianxuan.core.model.ExecutionMode
import top.wkbin.tianxuan.core.model.workflow.HostWorkflowActions
import top.wkbin.tianxuan.core.model.workflow.HostWorkflowPrivilege
import top.wkbin.tianxuan.core.model.workflow.NodeExecutionOutput
import top.wkbin.tianxuan.core.model.workflow.NodeRunStatus
import top.wkbin.tianxuan.core.model.workflow.WorkflowConditionEvaluator
import top.wkbin.tianxuan.core.model.workflow.WorkflowNode
import top.wkbin.tianxuan.core.model.workflow.WorkflowNodeType
import top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeContext
import top.wkbin.tianxuan.core.model.workflow.previousOutput
import top.wkbin.tianxuan.harness.capHostOutput
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.gui.HostGuiController
import top.wkbin.tianxuan.runtime.privilege.PrivilegeManager
import top.wkbin.tianxuan.runtime.shell.ShellCommand

class ConditionNodeExecutor() : NodeExecutor {
    override val supportedTypes = setOf(WorkflowNodeType.CONDITION_BRANCH)

    override suspend fun execute(
        node: WorkflowNode,
        context: WorkflowRuntimeContext,
        onProgress: suspend (NodeRunStatus, String) -> Unit,
    ): NodeExecutionOutput {
        val upstreamId = context.upstreamNodeIds?.lastOrNull()
        val upstream = upstreamId?.let { context.nodeOutputs[it] }
        val expression = node.config["expression"]?.takeIf { it.isNotBlank() }
            ?: node.config["condition"]?.takeIf { it.isNotBlank() }
            ?: "exitCode == 0"
        onProgress(NodeRunStatus.RUNNING, "Tính giá trị điều kiện: $expression")
        val result = WorkflowConditionEvaluator.evaluate(expression, context, upstream)
        val matched = result.matched
        return NodeExecutionOutput(
            status = NodeRunStatus.SUCCESS,
            exitCode = if (matched) 0 else 1,
            textOutput = buildString {
                append(if (matched) "✔ Điều kiện thỏa" else "✘ Điều kiện không thỏa")
                append('\n').append(result.detail)
                upstream?.textOutput?.takeIf { it.isNotBlank() }?.let {
                    append("\n\n—— Đầu ra thượng nguồn ——\n").append(it.take(8_000))
                }
            },
            variables = mapOf(
                "CONDITION_RESULT" to matched.toString(),
                "CONDITION_EXPRESSION" to expression,
            ),
        )
    }
}

class DelayNodeExecutor() : NodeExecutor {
    override val supportedTypes = setOf(WorkflowNodeType.DELAY)

    override suspend fun execute(
        node: WorkflowNode,
        context: WorkflowRuntimeContext,
        onProgress: suspend (NodeRunStatus, String) -> Unit,
    ): NodeExecutionOutput {
        val seconds = interpolate(node.config["seconds"] ?: node.config["delaySeconds"] ?: "1", context)
            .trim().toDoubleOrNull()?.coerceIn(0.0, 600.0) ?: 1.0
        val millis = (seconds * 1000).toLong().coerceAtLeast(0L)
        onProgress(NodeRunStatus.RUNNING, "Chờ ${seconds}s…")
        delay(millis)
        return NodeExecutionOutput(
            status = NodeRunStatus.SUCCESS,
            textOutput = "Đã chờ ${seconds}s",
            variables = mapOf("DELAY_SECONDS" to seconds.toString()),
            durationMs = millis,
        )
    }
}

class SetVariableNodeExecutor() : NodeExecutor {
    override val supportedTypes = setOf(WorkflowNodeType.SET_VARIABLE)

    override suspend fun execute(
        node: WorkflowNode,
        context: WorkflowRuntimeContext,
        onProgress: suspend (NodeRunStatus, String) -> Unit,
    ): NodeExecutionOutput {
        val assignments = linkedMapOf<String, String>()
        node.config["variables"]?.lines().orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val idx = line.indexOf('=')
                require(idx > 0) { "Định dạng gán biến phải là KEY=value: $line" }
                val key = line.substring(0, idx).trim()
                val value = interpolate(line.substring(idx + 1).trim(), context)
                require(KEY.matches(key)) { "Tên biến không hợp lệ: $key" }
                assignments[key] = value
            }
        // Also accept single key/value fields for simple editor forms.
        val singleKey = node.config["key"]?.trim().orEmpty()
        if (singleKey.isNotEmpty()) {
            require(KEY.matches(singleKey)) { "Tên biến không hợp lệ: $singleKey" }
            assignments[singleKey] = interpolate(node.config["value"].orEmpty(), context)
        }
        if (assignments.isEmpty()) {
            return NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 2, error = "Chưa cấu hình phép gán biến nào")
        }
        onProgress(NodeRunStatus.RUNNING, "Ghi ${assignments.size} biến")
        return NodeExecutionOutput(
            status = NodeRunStatus.SUCCESS,
            textOutput = assignments.entries.joinToString("\n") { "${it.key}=${it.value}" },
            variables = assignments,
        )
    }

    private companion object {
        val KEY = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}

class HostActionNodeExecutor(
    private val appContext: Context,
    private val gui: HostGuiController,
    private val privilegeManager: PrivilegeManager,
    private val linuxRuntime: LinuxRuntime,
    private val guiPilot: WorkflowGuiPilot,
) : NodeExecutor {
    override val supportedTypes = setOf(WorkflowNodeType.HOST_ACTION)

    override suspend fun execute(
        node: WorkflowNode,
        context: WorkflowRuntimeContext,
        onProgress: suspend (NodeRunStatus, String) -> Unit,
    ): NodeExecutionOutput = withContext(Dispatchers.IO) {
        val action = node.config["action"]?.trim().orEmpty().ifBlank { "status" }
        val def = HostWorkflowActions.find(action)
        onProgress(NodeRunStatus.RUNNING, "Động tác máy chủ: $action")
        if (def?.privilege == HostWorkflowPrivilege.PRIVILEGED) {
            requirePrivilege()?.let { err ->
                return@withContext NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 13, error = err)
            }
        }
        runCatching { dispatch(action, node, context, onProgress) }
            .fold(
                onSuccess = { it },
                onFailure = { err ->
                    if (err is kotlinx.coroutines.CancellationException) throw err
                    NodeExecutionOutput(
                        status = NodeRunStatus.FAILED,
                        exitCode = 1,
                        error = err.message ?: err::class.java.simpleName,
                        textOutput = err.message.orEmpty(),
                    )
                },
            )
    }

    private suspend fun dispatch(
        action: String,
        node: WorkflowNode,
        context: WorkflowRuntimeContext,
        onProgress: suspend (NodeRunStatus, String) -> Unit,
    ): NodeExecutionOutput {
        fun cfg(key: String): String = interpolate(node.config[key].orEmpty(), context).trim()
        fun requireCfg(key: String): String = cfg(key).also { require(it.isNotBlank()) { "Thiếu tham số: $key" } }

        return when (action) {
            "status" -> {
                val info = privilegeManager.getPrivilegeInfo()
                ok(
                    buildString {
                        append("Chế độ đang có hiệu lực:").append(info.mode.title)
                        append("\nTrạng thái quyền:").append(if (info.modeActive) "Đã cấp quyền" else "Chưa ủy quyền")
                        append("\nShizuku：").append(if (info.shizukuAvailable) "Khả dụng" else "Không khả dụng")
                        append("\nRoot：").append(if (info.rootAvailable) "Khả dụng" else "Không khả dụng hoặc chưa chọn")
                    },
                    mapOf(
                        "HOST_MODE" to info.mode.name,
                        "HOST_PRIVILEGED" to (info.mode != ExecutionMode.PROOT && info.modeActive).toString(),
                    ),
                )
            }
            "health" -> shellViaBridge("tianxuan-host health", context)
            "install-apk" -> {
                val artifact = node.config["artifactFrom"]?.let { context.nodeOutputs[it]?.artifacts?.lastOrNull() }
                    ?: context.globalVariables["APK_PATH"]?.takeIf { it.isNotBlank() }
                    ?: cfg("path").takeIf { it.isNotBlank() }
                    ?: error("Không tìm thấy APK: hãy cấu hình artifactFrom, path hoặc biến APK_PATH")
                shellViaBridge("tianxuan-host install-apk ${posixQuote(artifact)}", context, artifacts = listOf(artifact))
            }
            "device_status" -> privileged(
                "echo '[battery]'; dumpsys battery | grep -E 'level|status|temperature'" +
                    "; echo '[network]'; dumpsys connectivity | head -30" +
                    "; echo '[foreground]'; dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -6" +
                    "; echo '[storage]'; df -h /data | tail -2",
            )
            "logcat" -> {
                val lines = cfg("tail_lines").toIntOrNull()?.coerceIn(1, 2_000) ?: 200
                val tag = cfg("tag")
                val cmd = if (tag.isBlank()) "/system/bin/logcat -d -t $lines"
                else "/system/bin/logcat -d -t $lines -s ${shellQuote("$tag:*")}"
                privileged(cmd)
            }
            "app_launch" -> gui.launchApp(requireCfg("package")).toOutput()
            "app_force_stop" -> gui.forceStopApp(requireCfg("package")).toOutput()
            "app_clear_data" -> gui.clearAppData(requireCfg("package")).toOutput()
            "app_freeze", "package_disable" -> {
                val user = cfg("user").ifBlank { "0" }
                privileged("/system/bin/pm disable-user --user $user ${shellQuote(requireCfg("package"))}")
            }
            "app_unfreeze", "package_enable" -> {
                val user = cfg("user").ifBlank { "0" }
                privileged("/system/bin/pm enable --user $user ${shellQuote(requireCfg("package"))}")
            }
            "app_grant_permission" -> privileged(
                "/system/bin/pm grant ${shellQuote(requireCfg("package"))} ${shellQuote(requireCfg("permission"))}",
            )
            "app_revoke_permission" -> privileged(
                "/system/bin/pm revoke ${shellQuote(requireCfg("package"))} ${shellQuote(requireCfg("permission"))}",
            )
            "wait_foreground" -> {
                val timeout = (cfg("timeoutSeconds").toDoubleOrNull() ?: 15.0).coerceIn(1.0, 120.0)
                gui.waitForForeground(requireCfg("package"), (timeout * 1000).toLong()).toOutput(
                    extraVars = { mapOf("FOREGROUND_PKG" to requireCfg("package")) },
                )
            }
            "send_broadcast" -> sendBroadcast(node, context)
            "start_activity" -> startActivity(node, context)
            "start_service" -> {
                val component = requireCfg("component")
                val fg = cfg("foreground").equals("true", true)
                val verb = if (fg) "start-foreground-service" else "startservice"
                val cmd = buildString {
                    append("/system/bin/am $verb -n ${shellQuote(component)}")
                    cfg("intentAction").takeIf { it.isNotBlank() }?.let { append(" -a ${shellQuote(it)}") }
                    append(amExtras(parseExtras(cfg("extras"))))
                }
                privileged(cmd)
            }
            "settings_get" -> {
                val ns = requireNamespace(cfg("namespace"))
                val key = requireCfg("key")
                val result = privileged("/system/bin/settings get $ns ${shellQuote(key)}")
                val value = result.textOutput.lines().lastOrNull { it.isNotBlank() }.orEmpty().trim()
                val varName = cfg("outputVariable").ifBlank { "SETTINGS_VALUE" }
                result.copy(variables = result.variables + mapOf(varName to value, "SETTINGS_KEY" to key))
            }
            "settings_put" -> {
                val ns = requireNamespace(cfg("namespace"))
                val key = requireCfg("key")
                val value = requireCfg("value")
                if (ns == "system" && privilegeManager.writeSystemSetting(key, value)) {
                    ok("[Android API] settings put system $key = $value")
                } else {
                    val cmd = if (ns == "system" && key == "screen_brightness") {
                        val quoted = shellQuote(value)
                        "/system/bin/settings put system screen_brightness_mode 0; " +
                            "/system/bin/settings put system screen_brightness $quoted; " +
                            "echo \"requested=$quoted actual=\$(/system/bin/settings get system screen_brightness)\""
                    } else {
                        "/system/bin/settings put $ns ${shellQuote(key)} ${shellQuote(value)}"
                    }
                    privileged(cmd)
                }
            }
            "airplane_mode" -> {
                val enabled = parseBool(requireCfg("enabled"))
                val value = if (enabled) "1" else "0"
                privileged(
                    "/system/bin/settings put global airplane_mode_on $value; " +
                        "/system/bin/am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $enabled",
                )
            }
            "wifi_set" -> {
                val enabled = parseBool(requireCfg("enabled"))
                privileged("/system/bin/cmd wifi set-wifi-enabled ${if (enabled) "enabled" else "disabled"}")
            }
            "volume_set" -> {
                val value = requireCfg("value").toIntOrNull()?.coerceIn(0, 100)
                    ?: error("volume_set.value phải là số nguyên")
                privileged("/system/bin/media volume --show --stream 3 --set $value")
            }
            "screen_observe" -> {
                val only = cfg("onlyInteractive").ifBlank { "true" }.toBooleanStrictOrNull() ?: true
                gui.observeScreen(only).fold(
                    onSuccess = { obs ->
                        val (pkg, act) = obs.packageName to obs.activityName
                        ok(
                            obs.toAgentSummary(),
                            mapOf("FOREGROUND_PKG" to pkg, "FOREGROUND_ACTIVITY" to act),
                        )
                    },
                    onFailure = { error(it.message ?: "Cảm nhận màn hình thất bại") },
                )
            }
            "screen_click" -> gui.click(requireInt(cfg("x"), "x"), requireInt(cfg("y"), "y")).toOutput()
            "screen_double_click" -> gui.doubleClick(requireInt(cfg("x"), "x"), requireInt(cfg("y"), "y")).toOutput()
            "screen_long_press" -> gui.longPress(
                requireInt(cfg("x"), "x"),
                requireInt(cfg("y"), "y"),
                cfg("durationMs").toLongOrNull()?.coerceIn(200L, 5_000L) ?: 800L,
            ).toOutput()
            "screen_swipe" -> gui.swipe(
                requireInt(cfg("x1"), "x1"),
                requireInt(cfg("y1"), "y1"),
                requireInt(cfg("x2"), "x2"),
                requireInt(cfg("y2"), "y2"),
                cfg("durationMs").toLongOrNull()?.coerceIn(50L, 5_000L) ?: 300L,
            ).toOutput()
            "screen_scroll" -> {
                val direction = when (requireCfg("direction").lowercase()) {
                    "up" -> top.wkbin.tianxuan.runtime.gui.ScrollDirection.UP
                    "down" -> top.wkbin.tianxuan.runtime.gui.ScrollDirection.DOWN
                    "left" -> top.wkbin.tianxuan.runtime.gui.ScrollDirection.LEFT
                    "right" -> top.wkbin.tianxuan.runtime.gui.ScrollDirection.RIGHT
                    else -> error("direction chỉ hỗ trợ up/down/left/right")
                }
                val ratio = cfg("distanceRatio").toFloatOrNull()?.coerceIn(0.15f, 0.8f) ?: 0.45f
                val dur = cfg("durationMs").toLongOrNull()?.coerceIn(50L, 5_000L) ?: 350L
                gui.scroll(direction, ratio, dur).toOutput()
            }
            "screen_input_text", "paste_text" -> gui.inputText(requireCfg("text")).toOutput()
            "screen_key" -> gui.sendKey(requireCfg("key")).toOutput()
            "screen_capture" -> gui.captureScreenshot(requireCfg("path")).toOutput(
                artifacts = { listOf(requireCfg("path")) },
            )
            "gui_pilot" -> {
                val goal = cfg("goal").ifBlank { context.globalVariables["GUI_GOAL"].orEmpty() }
                val pkg = cfg("package").ifBlank { context.globalVariables["TARGET_PACKAGE"].orEmpty() }
                val maxSteps = cfg("maxSteps").toIntOrNull()?.coerceIn(1, 40) ?: 18
                guiPilot.run(
                    goal = goal,
                    targetPackage = pkg.takeIf { it.isNotBlank() },
                    modelId = context.globalVariables["WORKFLOW_MODEL_ID"],
                    modelVariant = context.globalVariables["WORKFLOW_MODEL_VARIANT"],
                    maxSteps = maxSteps,
                    onProgress = { msg -> onProgress(NodeRunStatus.STREAMING, msg) },
                )
            }
            "toast" -> gui.showToast(requireCfg("text")).toOutput()
            "vibrate" -> gui.vibrate(cfg("durationMs").toLongOrNull() ?: 200L).toOutput()
            "clipboard_set" -> gui.clipboardSet(requireCfg("text")).toOutput()
            "clipboard_get" -> gui.clipboardGet().fold(
                onSuccess = { text -> ok(text.ifBlank { "(Trống)" }, mapOf("CLIPBOARD" to text)) },
                onFailure = { error(it.message ?: "Đọc clipboard thất bại") },
            )
            "notification" -> postNotification(requireCfg("title"), requireCfg("text"))
            "shell" -> privileged(requireCfg("command"))
            "cmd" -> {
                val service = requireCfg("service")
                val args = cfg("args")
                privileged("/system/bin/cmd $service" + if (args.isBlank()) "" else " $args")
            }
            else -> {
                // Escape hatch: treat unknown action as host shell command template.
                val command = cfg("command").ifBlank { error("Thao tác host không xác định: $action; hãy chọn thao tác được hỗ trợ trong editor, hoặc cung cấp command") }
                requirePrivilege()?.let { err -> error(err) }
                privileged(command)
            }
        }.also { onProgress(NodeRunStatus.STREAMING, it.textOutput.takeLast(4_000)) }
    }

    private suspend fun sendBroadcast(node: WorkflowNode, context: WorkflowRuntimeContext): NodeExecutionOutput {
        fun cfg(key: String) = interpolate(node.config[key].orEmpty(), context).trim()
        val action = cfg("intentAction").ifBlank { error("Thiếu intentAction") }
        val pkg = cfg("package").ifBlank { null }
        val component = cfg("component").ifBlank { null }
        val extras = parseExtras(cfg("extras"))
        val forceShell = cfg("preferShell").equals("true", true)
        if (!forceShell) {
            val soft = gui.sendBroadcastIntent(action, pkg, component, extras)
            if (soft.isSuccess) return soft.toOutput()
        }
        requirePrivilege()?.let { error(it) }
        val cmd = buildString {
            append("/system/bin/am broadcast -a ${shellQuote(action)}")
            pkg?.let { append(" -p ${shellQuote(it)}") }
            component?.let { append(" -n ${shellQuote(it)}") }
            append(amExtras(extras))
        }
        return privileged(cmd)
    }

    private suspend fun startActivity(node: WorkflowNode, context: WorkflowRuntimeContext): NodeExecutionOutput {
        fun cfg(key: String) = interpolate(node.config[key].orEmpty(), context).trim()
        val component = cfg("component").ifBlank { null }
        val intentAction = cfg("intentAction").ifBlank { null }
        val dataUri = cfg("dataUri").ifBlank { null }
        val mime = cfg("mimeType").ifBlank { null }
        val extras = parseExtras(cfg("extras"))
        val forceShell = cfg("preferShell").equals("true", true)
        if (!forceShell) {
            val soft = gui.startActivityIntent(component, intentAction, dataUri, mime, extras)
            if (soft.isSuccess) return soft.toOutput()
        }
        requirePrivilege()?.let { error(it) }
        val cmd = buildString {
            append("/system/bin/am start")
            component?.let { append(" -n ${shellQuote(it)}") }
            intentAction?.let { append(" -a ${shellQuote(it)}") }
            dataUri?.let { append(" -d ${shellQuote(it)}") }
            mime?.let { append(" -t ${shellQuote(it)}") }
            append(amExtras(extras))
        }
        return privileged(cmd)
    }

    private fun postNotification(title: String, text: String): NodeExecutionOutput {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channelId = "tianxuan_workflow"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Luồng công việc Thiên Huyền", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notification = Notification.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        manager.notify(id, notification)
        return ok("Đã gửi thông báo: $title", mapOf("NOTIFICATION_ID" to id.toString()))
    }

    private suspend fun privileged(command: String): NodeExecutionOutput {
        requirePrivilege()?.let { return NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 13, error = it) }
        val result = privilegeManager.executeShellCommand(command)
        return NodeExecutionOutput(
            status = if (result.success) NodeRunStatus.SUCCESS else NodeRunStatus.FAILED,
            exitCode = result.exitCode,
            // dumpsys 等宿主命令输出可达数 MB：workflow 节点输出会常驻执行状态与变量表，
            // 不截断会把 256MB 的 Java 堆直接拖爆（target footprint OOM）。
            textOutput = capHostOutput(
                listOf(result.stdout, result.stderr).filter { it.isNotBlank() }.joinToString("\n").trim(),
            ),
            error = result.stderr.takeIf { !result.success }?.ifBlank { "Lệnh máy chủ thất bại exit=${result.exitCode}" },
        )
    }

    private suspend fun shellViaBridge(
        command: String,
        context: WorkflowRuntimeContext,
        artifacts: List<String> = emptyList(),
    ): NodeExecutionOutput {
        val result = linuxRuntime.execute(
            ShellCommand(commandLine = command, workingDirectory = context.workspacePath, timeoutMs = 120_000L),
        )
        return NodeExecutionOutput(
            status = if (result.isSuccess) NodeRunStatus.SUCCESS else NodeRunStatus.FAILED,
            exitCode = result.exitCode,
            textOutput = listOf(result.stdout, result.stderr).filter { it.isNotBlank() }.joinToString("\n").trim(),
            artifacts = artifacts,
            durationMs = result.durationMs,
            error = result.stderr.takeIf { !result.isSuccess },
        )
    }

    private suspend fun requirePrivilege(): String? {
        val info = privilegeManager.getPrivilegeInfo()
        return if (info.mode == ExecutionMode.PROOT || !info.modeActive) {
            "Thao tác này cần Shizuku hoặc Root. Vui lòng cấp quyền trong cài đặt và chuyển chế độ thực thi rồi chạy lại."
        } else null
    }

    private fun ok(text: String, variables: Map<String, String> = emptyMap()) =
        NodeExecutionOutput(NodeRunStatus.SUCCESS, textOutput = text, variables = variables)

    private fun Result<String>.toOutput(
        artifacts: () -> List<String> = { emptyList() },
        extraVars: () -> Map<String, String> = { emptyMap() },
    ): NodeExecutionOutput = fold(
        onSuccess = { ok(it, extraVars()) .let { out -> out.copy(artifacts = artifacts()) } },
        onFailure = { NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 1, error = it.message, textOutput = it.message.orEmpty()) },
    )

    private fun requireNamespace(raw: String): String {
        val ns = raw.lowercase()
        require(ns in setOf("system", "secure", "global")) { "namespace chỉ hỗ trợ system/secure/global" }
        return ns
    }

    private fun requireInt(raw: String, name: String): Int =
        raw.toIntOrNull() ?: error("$name phải là số nguyên")

    private fun parseBool(raw: String): Boolean =
        raw.equals("true", true) || raw == "1" || raw.equals("on", true) || raw.equals("yes", true)

    private fun parseExtras(raw: String): Map<String, String> = raw.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }.toMap()

    private fun amExtras(extras: Map<String, String>): String = buildString {
        extras.forEach { (rawKey, value) ->
            val parts = rawKey.split(':', limit = 2)
            val (key, type) = when {
                parts.size == 2 && parts[1] in setOf("int", "long", "bool", "boolean", "float", "uri", "string") ->
                    parts[0] to parts[1]
                parts.size == 2 && parts[0] in setOf("int", "long", "bool", "boolean", "float", "uri", "string") ->
                    parts[1] to parts[0]
                else -> rawKey to "string"
            }
            when (type) {
                "int" -> append(" --ei ${shellQuote(key)} ${value.toInt()}")
                "long" -> append(" --el ${shellQuote(key)} ${value.toLong()}")
                "bool", "boolean" -> append(" --ez ${shellQuote(key)} ${parseBool(value)}")
                "float" -> append(" --ef ${shellQuote(key)} ${value.toFloat()}")
                "uri" -> append(" --eu ${shellQuote(key)} ${shellQuote(value)}")
                else -> append(" --es ${shellQuote(key)} ${shellQuote(value)}")
            }
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    private fun posixQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}

internal fun interpolate(template: String, context: WorkflowRuntimeContext): String =
    HOST_VARIABLE.replace(template) { match ->
        val key = match.groupValues[1]
        when {
            key == "WORKSPACE_PATH" -> context.workspacePath
            key == "previous.output" -> context.previousOutput()
            key.endsWith(".output") -> context.nodeOutputs[key.removeSuffix(".output")]?.textOutput.orEmpty()
            else -> context.globalVariables[key].orEmpty()
        }
    }

private val HOST_VARIABLE = Regex("\\$\\{([A-Za-z0-9_.\\-]+)\\}")
