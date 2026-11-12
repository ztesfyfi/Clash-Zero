package com.github.kr328.clash.service.data

/**
 * Validates nested strategy-group references before saving or generating config.
 * Returns a readable error when a cycle or missing reference is found.
 */
object StrategyGraphValidator {
    data class Reference(val groupId: String, val memberType: String, val memberId: String)

    fun validate(
        strategyGroupIds: Set<String>,
        nodeGroupIds: Set<String>,
        references: Collection<Reference>,
    ): String? {
        val graph = strategyGroupIds.associateWith { mutableListOf<String>() }

        for (reference in references) {
            when (reference.memberType) {
                StrategyGroupMember.TYPE_NODE_GROUP -> {
                    if (reference.memberId !in nodeGroupIds) {
                        return "Node group not found: ${reference.memberId}"
                    }
                }
                StrategyGroupMember.TYPE_STRATEGY_GROUP -> {
                    if (reference.memberId !in strategyGroupIds) {
                        return "Strategy group not found: ${reference.memberId}"
                    }
                    if (reference.groupId !in strategyGroupIds) {
                        return "Parent strategy group not found: ${reference.groupId}"
                    }
                    graph.getValue(reference.groupId).add(reference.memberId)
                }
                else -> return "Unsupported member type: ${reference.memberType}"
            }
        }

        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(id: String): Boolean {
            if (id in visiting) return false
            if (id in visited) return true
            visiting += id
            for (child in graph[id].orEmpty()) {
                if (!visit(child)) return false
            }
            visiting -= id
            visited += id
            return true
        }

        for (id in strategyGroupIds) {
            if (!visit(id)) return "Circular strategy-group reference detected"
        }
        return null
    }
}
