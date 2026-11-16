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
        if (conditions.map { it.conditionGroup }.distinct().size != 1) {
            return "当前版本每个目标集合暂时只支持一个规则组"
        }
        val groupMode = conditions.first().groupMode
        if (groupMode != TargetCondition.GROUP_ALL && groupMode != TargetCondition.GROUP_ANY) {
            return "不支持的规则组关系：$groupMode"
        }
        if ((targetSet.matchMode == TargetSet.MATCH_ALL) != (groupMode == TargetCondition.GROUP_ALL)) {
            return "目标集合匹配关系与规则组关系不一致"
        }

        for (condition in conditions) {
            val value = condition.matcherValue.trim()
            if (value.isEmpty()) return "匹配值不能为空"
            when (condition.matcherType) {
                PolicyCondition.DOMAIN, PolicyCondition.DOMAIN_SUFFIX -> {
                    val domain = value.removePrefix(".")
                    if (!domainPattern.matches(domain)) return "无效域名：$value"
                }
                PolicyCondition.DOMAIN_KEYWORD -> if (value.any { it.isWhitespace() || it == ',' }) {
                    return "域名关键词不能包含空格或逗号"
                }
                PolicyCondition.IP -> if (!isIpAddress(value)) return "无效 IP 地址：$value"
                PolicyCondition.IP_CIDR -> if (!isCidr(value)) return "无效 IP 网段：$value"
                PolicyCondition.GEOIP -> if (!value.matches(Regex("(?i)^[a-z]{2}$"))) {
                    return "GeoIP 必须使用两位国家代码"
                }
                PolicyCondition.GEOSITE -> if (!value.matches(Regex("(?i)^[a-z0-9_-]+$"))) {
                    return "无效 GeoSite 标签：$value"
                }
                PolicyCondition.IP_SET, PolicyCondition.DOMAIN_SET -> {
                    val set = setsById[value] ?: return "引用的规则集不存在：$value"
                    val expected = if (condition.matcherType == PolicyCondition.IP_SET) {
                        RuleSet.KIND_IP
                    } else RuleSet.KIND_DOMAIN
                    if (set.kind != expected) return "规则集类型与匹配类型不一致：${set.name}"
                    if (!set.enabled) return "引用的规则集已停用：${set.name}"
                    if (set.updateInterval < 3600000L) return "规则集更新间隔不能少于 1 小时：${set.name}"
                    if (set.format !in setOf(RuleSet.FORMAT_MRS, RuleSet.FORMAT_CLASSICAL, RuleSet.FORMAT_TEXT)) {
                        return "规则集格式不受支持：${set.name}"
                    }
                    when (set.sourceType) {
                        RuleSet.SOURCE_URL -> if (!(set.sourceValue.startsWith("https://", true) ||
                            set.sourceValue.startsWith("http://", true))) return "规则集 URL 必须使用 HTTP(S)：${set.name}"
                        RuleSet.SOURCE_FILE -> {
                            val file = java.io.File(set.sourceValue)
                            if (!file.isAbsolute || !file.isFile || !file.canRead()) {
                                return "规则集本地文件不存在或不可读取：${set.name}"
                            }
                        }
                        else -> return "规则集来源类型不受支持：${set.name}"
                    }
                }
                else -> return "不支持的匹配类型：${condition.matcherType}"
            }
        }
        return null
    }

    private fun isIpAddress(value: String): Boolean = isIpv4(value) || isIpv6(value)

    private fun isIpv6(value: String): Boolean {
        if (!value.contains(':') || value.contains('%') || !value.matches(Regex("(?i)^[0-9a-f:.]+$"))) return false
        if (value.count { it == ':' } < 2 || value.contains(":::")) return false
        val compressed = value.contains("::")
        if (compressed && value.indexOf("::") != value.lastIndexOf("::")) return false
        val halves = if (compressed) value.split("::", limit = 2) else listOf(value, "")
        fun countGroups(part: String): Int {
            if (part.isEmpty()) return 0
            val segments = part.split(':')
            var count = 0
            for ((index, segment) in segments.withIndex()) {
                if (segment.isEmpty()) return -1
                if (segment.contains('.')) {
                    if (index != segments.lastIndex || !isIpv4(segment)) return -1
                    count += 2
                } else {
                    if (segment.length !in 1..4) return -1
                    count++
                }
            }
            return count
        }
        val left = countGroups(halves[0])
        val right = countGroups(halves[1])
        if (left < 0 || right < 0) return false
        return if (compressed) left + right < 8 else left + right == 8
    }

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
