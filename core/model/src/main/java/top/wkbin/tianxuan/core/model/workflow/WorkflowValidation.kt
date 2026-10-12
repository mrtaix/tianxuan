package top.wkbin.tianxuan.core.model.workflow

data class WorkflowValidationIssue(val field: String, val message: String)

object WorkflowValidator {
    fun validate(definition: WorkflowDefinition): List<WorkflowValidationIssue> {
        val issues = mutableListOf<WorkflowValidationIssue>()
        if (definition.id.isBlank()) issues += WorkflowValidationIssue("id", "ID workflow không được rỗng")
        if (definition.name.isBlank()) issues += WorkflowValidationIssue("name", "Tên workflow không được rỗng")
        if (definition.nodes.isEmpty()) issues += WorkflowValidationIssue("nodes", "Workflow cần ít nhất một nút")

        val nodeIds = definition.nodes.map { it.id }
        if (nodeIds.size != nodeIds.distinct().size) {
            issues += WorkflowValidationIssue("nodes", "ID nút phải duy nhất")
        }
        definition.nodes.forEach { node ->
            if (node.id.isBlank()) issues += WorkflowValidationIssue("nodes", "ID nút không được để trống")
            if (node.title.isBlank()) issues += WorkflowValidationIssue("nodes.${node.id}.title", "Tiêu đề nút không được để trống")
            if (node.timeoutSeconds !in 1..3600) {
                issues += WorkflowValidationIssue("nodes.${node.id}.timeoutSeconds", "Thời gian chờ phải nằm giữa 1–3600 giây")
            }
            if (node.type == WorkflowNodeType.CONDITION_BRANCH) {
                val expression = node.config["expression"] ?: node.config["condition"]
                WorkflowConditionEvaluator.validationError(expression)?.let { message ->
                    issues += WorkflowValidationIssue("nodes.${node.id}.expression", message)
                }
            }
            if (node.type == WorkflowNodeType.HOST_ACTION) {
                val action = node.config["action"].orEmpty().ifBlank { "status" }
                val def = HostWorkflowActions.find(action)
                if (def == null && node.config["command"].isNullOrBlank()) {
                    issues += WorkflowValidationIssue(
                        "nodes.${node.id}.action",
                        "Động tác máy chủ không rõ: $action (có thể đổi sang động tác đã biết, hoặc cung cấp command làm cổng thoát)",
                    )
                }
                def?.fields?.filter { it.required }?.forEach { field ->
                    if (node.config[field.key].isNullOrBlank()) {
                        issues += WorkflowValidationIssue(
                            "nodes.${node.id}.${field.key}",
                            "Thao tác máy chủ 「${def.label}」 thiếu tham số bắt buộc: ${field.label}",
                        )
                    }
                }
            }
            if (node.type == WorkflowNodeType.DELAY) {
                val seconds = node.config["seconds"] ?: node.config["delaySeconds"]
                if (seconds != null && seconds.toDoubleOrNull() == null) {
                    issues += WorkflowValidationIssue("nodes.${node.id}.seconds", "Số giây chờ phải là số")
                }
            }
        }

        val known = nodeIds.toSet()
        val edgeIds = definition.edges.map { it.id }
        if (edgeIds.size != edgeIds.distinct().size) {
            issues += WorkflowValidationIssue("edges", "ID kết nối phải duy nhất")
        }
        definition.edges.forEach { edge ->
            if (edge.id.isBlank()) issues += WorkflowValidationIssue("edges", "ID kết nối không được rỗng")
            if (edge.fromNodeId !in known || edge.toNodeId !in known) {
                issues += WorkflowValidationIssue("edges.${edge.id}", "Cạnh tham chiếu nút không tồn tại")
            }
            if (edge.fromNodeId == edge.toNodeId) {
                issues += WorkflowValidationIssue("edges.${edge.id}", "Node không thể kết nối tới chính nó")
            }
            WorkflowEdgeCondition.validationError(edge)?.let { message ->
                issues += WorkflowValidationIssue("edges.${edge.id}.condition", message)
            }
        }

        if (known.isNotEmpty() && containsCycle(definition)) {
            issues += WorkflowValidationIssue("edges", "Workflow phải là đồ thị không chu trình (DAG)")
        }
        return issues
    }

    private fun containsCycle(definition: WorkflowDefinition): Boolean {
        val indegree = definition.nodes.associate { it.id to 0 }.toMutableMap()
        val outgoing = definition.edges.groupBy { it.fromNodeId }
        definition.edges.forEach { edge ->
            if (edge.toNodeId in indegree && edge.fromNodeId in indegree) {
                indegree[edge.toNodeId] = (indegree[edge.toNodeId] ?: 0) + 1
            }
        }
        val ready = ArrayDeque(indegree.filterValues { it == 0 }.keys)
        var visited = 0
        while (ready.isNotEmpty()) {
            val id = ready.removeFirst()
            visited++
            outgoing[id].orEmpty().forEach { edge ->
                if (edge.toNodeId in indegree) {
                    val next = (indegree[edge.toNodeId] ?: 1) - 1
                    indegree[edge.toNodeId] = next
                    if (next == 0) ready.add(edge.toNodeId)
                }
            }
        }
        return visited != definition.nodes.size
    }
}
