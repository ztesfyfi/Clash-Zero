package com.github.kr328.clash.service.data

/** Validates target-set matchers and referenced reusable rule sets. */
object TargetSetValidator {
    private val domainPattern = Regex("^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\\.(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*\\.?$")

    fun validate(
        targetSet: TargetSet,
        conditions: Collection<TargetCondition>,
        ruleSets: Collection<RuleSet>,
    ): String? {
        if (targetSet.name.isBlank()) return "目标集合名称不能为空"
        if (targetSet.matchMode != TargetSet.MATCH_ALL && targetSet.matchMode != TargetSet.MATCH_ANY) {
            return "不支持的目标集合匹配模式：${targetSet.matchMode}"
        }
        if (conditions.isEmpty()) return "目标集合至少需要一条匹配规则"
        if (conditions.any { it.targetSetId != targetSet.id }) return "匹配规则所属目标集合不一致"

        val setsById = ruleSets.associateBy { it.id }
        val groupModes = conditions.groupBy { it.conditionGroup }.values
        if (groupModes.any { group -> group.map { it.groupMode }.distinct().size > 1 }) {
            return "同一条件组不能混用 AND 和 OR"
        }

        for (condition in conditions) {
            val value = condition.matcherValue.trim()
            if (value.isEmpty()) return "匹配值不能为空"
            when (condition.matcherType) {
                PolicyCondition.DOMAIN, PolicyCondition.DOMAIN_SUFFIX -> {
                    val domain = value.removePrefix(".")
                    if (!domainPattern.matches(domain)) return "无效域名：$value"
                }
                PolicyCondition.DOMAIN_KEYWORD -> Unit
                PolicyCondition.IP -> if (!isIpAddress(value)) return "无效 IP 地址：$value"
                PolicyCondition.IP_CIDR -> if (!isCidr(value)) return "无效 IP 网段：$value"
                PolicyCondition.GEOIP -> if (!value.matches(Regex("(?i)^[a-z]{2}$"))) {
                    return "GeoIP 必须使用两位国家代码"
                }
                PolicyCondition.GEOSITE -> Unit
                PolicyCondition.IP_SET, PolicyCondition.DOMAIN_SET -> {
                    val set = setsById[value] ?: return "引用的规则集不存在：$value"
                    val expected = if (condition.matcherType == PolicyCondition.IP_SET) {
                        RuleSet.KIND_IP
                    } else RuleSet.KIND_DOMAIN
                    if (set.kind != expected) return "规则集类型与匹配类型不一致：${set.name}"
                    if (!set.enabled) return "引用的规则集已停用：${set.name}"
                }
                else -> return "不支持的匹配类型：${condition.matcherType}"
            }
        }
        return null
    }

    private fun isIpAddress(value: String): Boolean =
        isIpv4(value) || (value.contains(':') && value.matches(Regex("(?i)^[0-9a-f:]+$")) && !value.contains(":::"))

    private fun isCidr(value: String): Boolean {
        val parts = value.split('/')
        if (parts.size != 2) return false
        val address = parts[0]
        val prefix = parts[1].toIntOrNull() ?: return false
        return when {
            isIpv4(address) -> prefix in 0..32
            address.contains(':') && isIpAddress(address) -> prefix in 0..128
            else -> false
        }
    }

    private fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
