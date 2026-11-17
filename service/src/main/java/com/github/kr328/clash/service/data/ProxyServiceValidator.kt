package com.github.kr328.clash.service.data

/** Validates proxy-service outbounds and the target-set ownership relation. */
object ProxyServiceValidator {
    fun validate(
        services: List<ProxyService>,
        nodeGroups: List<NodeGroup>,
        targetSets: List<TargetSet>,
        nodeMembers: Map<String, List<ProxyServiceNodeGroup>>,
        bindings: List<ProxyServiceTargetSet>,
    ): String? {
        val serviceMap = services.associateBy { it.id }
        val groupMap = nodeGroups.associateBy { it.id }
        val targetMap = targetSets.associateBy { it.id }

        if (serviceMap.size != services.size) return "代理服务 ID 重复"
        if (services.map { it.name.trim().lowercase() }.distinct().size != services.size) {
            return "代理服务名称重复"
        }

        for (service in services.filter { it.enabled }) {
            if (service.name.isBlank()) return "代理服务名称不能为空"
            if (service.mode !in setOf(
                    ProxyService.MODE_SELECT,
                    ProxyService.MODE_URL_TEST,
                    ProxyService.MODE_FALLBACK,
                    ProxyService.MODE_LOAD_BALANCE,
                )
            ) {
                return "代理服务模式无效：${service.mode}"
            }

            val members = nodeMembers[service.id].orEmpty()
            if (members.isEmpty()) return "代理服务“${service.name}”没有选择节点组"
            if (members.map { it.nodeGroupId }.distinct().size != members.size) {
                return "代理服务“${service.name}”重复选择节点组"
            }

            for (member in members) {
                val group = groupMap[member.nodeGroupId]
                    ?: return "代理服务“${service.name}”引用了不存在的节点组"
                if (!group.enabled) {
                    return "代理服务“${service.name}”引用了已停用的节点组“${group.name}”"
                }
            }

            if (service.mode == ProxyService.MODE_LOAD_BALANCE) {
                val regions = members
                    .mapNotNull { groupMap[it.nodeGroupId]?.regionCode?.uppercase() }
                    .distinct()
                if (regions.size > 1) {
                    return "代理服务“${service.name}”的负载均衡节点组包含多个已标记地区"
                }
            }
        }

        val owners = mutableMapOf<String, String>()
        for (binding in bindings) {
            if (binding.serviceId !in serviceMap) return "目标集合绑定了不存在的代理服务"
            if (binding.targetSetId !in targetMap) return "代理服务绑定了不存在的目标集合"
            if (owners.put(binding.targetSetId, binding.serviceId) != null) {
                return "同一个目标集合不能绑定多个代理服务"
            }
        }

        // Unbound or disabled target sets remain valid. They are intentionally
        // omitted from generated routing rules until an enabled service owns them.
        return null
    }
}
