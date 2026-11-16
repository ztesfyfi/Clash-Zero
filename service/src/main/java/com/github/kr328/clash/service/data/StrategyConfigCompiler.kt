package com.github.kr328.clash.service.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Compiles the visual strategy model into a small JSON overlay consumed by the
 * native Mihomo config loader. The base subscription config is never rewritten.
 */
object StrategyConfigCompiler {
    private const val PREFIX = "CZ-"
    private const val TEST_URL = "https://www.gstatic.com/generate_204"

    suspend fun writeOverlay(context: Context, profileUuid: UUID) {
        val database = Database.database
        val nodeGroups = database.openNodeGroupDao().queryAll().filter { it.enabled }
        val strategies = database.openStrategyGroupDao().queryAll().filter { it.enabled }
        val policies = database.openRoutingPolicyDao().queryAll().filter { it.enabled }

        val nodeGroupIds = nodeGroups.map { it.id }.toSet()
        val strategyIds = strategies.map { it.id }.toSet()
        val strategyMembers = strategies.associate { group ->
            group.id to database.openStrategyGroupDao().queryMembers(group.id)
        }
        val references = strategyMembers.flatMap { (groupId, members) ->
            members.map { StrategyGraphValidator.Reference(groupId, it.memberType, it.memberId) }
        }
        val graphError = StrategyGraphValidator.validate(strategyIds, nodeGroupIds, references)
        require(graphError == null) { graphError ?: "代理策略引用无效" }

        val staticMembers = nodeGroups.filter { it.mode == NodeGroup.MODE_STATIC }.associate { group ->
            group.id to database.openNodeGroupDao().queryMembers(group.id)
                .filter { it.profileUuid == profileUuid }
                .map { it.proxyName }
                .distinct()
        }
        // Only successful, timestamped lookups for this exact subscription qualify.
        val verifiedOverrides = database.openProfileNodeDao().queryOverrides(profileUuid)
            .filter {
                !it.exitIp.isNullOrBlank() && it.ipCheckedAt != null &&
                    it.ipCheckError.isNullOrBlank() && !it.exitCountry.isNullOrBlank()
            }
        val regionMembers = nodeGroups.filter { it.mode == NodeGroup.MODE_NAME_REGION }.associate { group ->
            group.id to verifiedOverrides
                .filter { it.exitCountry.equals(group.regionCode, ignoreCase = true) }
                .map { it.originalName }
                .distinct()
        }
        val nodeNames = nodeGroups.filter { group ->
            group.id in strategyMembers.values.flatten()
                .filter { it.memberType == StrategyGroupMember.TYPE_NODE_GROUP }
                .map { it.memberId } &&
                when (group.mode) {
                    NodeGroup.MODE_STATIC -> staticMembers[group.id].orEmpty().isNotEmpty()
                    NodeGroup.MODE_NAME -> group.nameTag == profileUuid.toString()
                    NodeGroup.MODE_NAME_REGION -> group.nameTag == profileUuid.toString() &&
                        regionMembers[group.id].orEmpty().isNotEmpty()
                    else -> false
                }
        }.associate { it.id to nodeGroupName(it.id) }
        val availableStrategyIds = strategies.map { it.id }.toSet()
        val usableStrategies = strategies.filter { group ->
            val members = strategyMembers[group.id].orEmpty()
            members.isNotEmpty() && members.all { member ->
                when (member.memberType) {
                    StrategyGroupMember.TYPE_NODE_GROUP -> member.memberId in nodeNames
                    StrategyGroupMember.TYPE_STRATEGY_GROUP -> member.memberId in availableStrategyIds
                    else -> false
                }
            }
        }.map { it.id }.toMutableSet()
        var removed: Boolean
        do {
            removed = usableStrategies.removeAll { id ->
                strategyMembers[id].orEmpty().any {
                    it.memberType == StrategyGroupMember.TYPE_STRATEGY_GROUP && it.memberId !in usableStrategies
                }
            }
        } while (removed)
        val strategyNames = usableStrategies.associateWith(::strategyName)
        val proxyGroups = JSONArray()

        for (group in nodeGroups.filter { it.id in nodeNames.keys }) {
            val json = JSONObject()
                .put("name", nodeNames.getValue(group.id))
                .put("type", "select")
            when (group.mode) {
                NodeGroup.MODE_STATIC -> json.put("proxies", JSONArray(staticMembers[group.id].orEmpty()))
                NodeGroup.MODE_NAME -> {
                    json.put("include-all", true)
                    group.nameKeyword?.takeIf { it.isNotBlank() }?.let { keyword ->
                        json.put("filter", "(?i).*" + Regex.escape(keyword) + ".*")
                    }
                }
                NodeGroup.MODE_NAME_REGION -> {
                    val members = regionMembers[group.id].orEmpty()
                    require(members.isNotEmpty()) {
                        "地区动态组“${group.name}”没有已验证且匹配地区的节点；请先查询节点真实出口 IP"
                    }
                    json.put("proxies", JSONArray(members))
                }
            }
            proxyGroups.put(json)
        }

        for (group in strategies.filter { it.id in usableStrategies }) {
            val members = strategyMembers[group.id].orEmpty().sortedBy { it.position }.mapNotNull { member ->
                when (member.memberType) {
                    StrategyGroupMember.TYPE_NODE_GROUP -> nodeNames[member.memberId]
                    StrategyGroupMember.TYPE_STRATEGY_GROUP -> strategyNames[member.memberId]
                    else -> null
                }
            }
            if (members.isEmpty()) continue
            val type = when (group.mode) {
                StrategyGroup.MODE_URL_TEST -> "url-test"
                StrategyGroup.MODE_FALLBACK -> "fallback"
                StrategyGroup.MODE_LOAD_BALANCE -> "load-balance"
                else -> "select"
            }
            val json = JSONObject()
                .put("name", strategyNames.getValue(group.id))
                .put("type", type)
                .put("proxies", JSONArray(members))
            if (type == "url-test" || type == "fallback") {
                json.put("url", TEST_URL)
                    .put("interval", if (type == "fallback") 60 else 300)
                    .put("timeout", 5000)
                    .put("max-failed-times", 2)
            }
            if (type == "load-balance") json.put("strategy", "consistent-hashing")
            proxyGroups.put(json)
        }

        val rules = JSONArray()
        fun matcherExpression(condition: PolicyCondition): String {
            require(condition.matcherType != PolicyCondition.IP_SET &&
                condition.matcherType != PolicyCondition.DOMAIN_SET) {
                "目标分流引用规则集；规则集提供器尚未接入配置生成"
            }
            val type = when (condition.matcherType) {
                PolicyCondition.DOMAIN -> "DOMAIN"
                PolicyCondition.DOMAIN_SUFFIX -> "DOMAIN-SUFFIX"
                PolicyCondition.DOMAIN_KEYWORD -> "DOMAIN-KEYWORD"
                PolicyCondition.IP, PolicyCondition.IP_CIDR -> "IP-CIDR"
                PolicyCondition.GEOIP -> "GEOIP"
                PolicyCondition.GEOSITE -> "GEOSITE"
                else -> error("目标分流包含不支持的匹配类型：" + condition.matcherType)
            }
            var value = condition.matcherValue.trim()
            if (condition.matcherType == PolicyCondition.IP && !value.contains('/')) {
                value += if (value.contains(':')) "/128" else "/32"
            }
            val expression = "$type,$value"
            return if (condition.negated) "NOT,(($expression))" else expression
        }

        for (policy in policies.sortedWith(compareBy<RoutingPolicy> { it.priority }.thenBy { it.createdAt })) {
            val conditions = database.openRoutingPolicyDao().queryConditions(policy.id)
            if (conditions.isEmpty()) continue
            val target = when (policy.targetType) {
                RoutingPolicy.TARGET_DIRECT -> "DIRECT"
                RoutingPolicy.TARGET_REJECT -> "REJECT"
                RoutingPolicy.TARGET_STRATEGY_GROUP -> strategyNames[policy.targetId] ?: continue
                else -> error("目标分流“" + policy.name + "”包含不支持的出口类型")
            }
            val expressions = conditions.map(::matcherExpression)
            if (policy.matchMode == RoutingPolicy.MATCH_ALL) {
                rules.put("AND,((" + expressions.joinToString("),(") + "))," + target)
            } else {
                expressions.forEach { rules.put("$it,$target") }
            }
        }

        val overlay = JSONObject()
            .put("proxy-groups", proxyGroups)
            .put("rules", rules)
        val directory = context.filesDir.resolve("imported").resolve(profileUuid.toString())
        require(directory.isDirectory) { "当前订阅配置目录不存在" }
        val destination = File(directory, "strategy.json")
        val temporary = File(directory, "strategy.json.tmp")
        temporary.writeText(overlay.toString(), Charsets.UTF_8)
        check(temporary.renameTo(destination)) { "无法原子更新 Mihomo 策略配置" }
    }

    private fun nodeGroupName(id: String) = PREFIX + "NG-" + id.replace("-", "").take(20)
    private fun strategyName(id: String) = PREFIX + "SG-" + id.replace("-", "").take(20)
}
