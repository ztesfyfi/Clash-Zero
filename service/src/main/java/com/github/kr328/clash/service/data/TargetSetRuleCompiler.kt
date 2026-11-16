package com.github.kr328.clash.service.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Converts independently managed target sets into Mihomo rule-providers and rules.
 *
 * A target set deliberately has no outbound reference. The proxy-service layer supplies
 * targetSetId -> outboundName bindings when it is implemented.
 */
object TargetSetRuleCompiler {
    data class Output(
        val providers: JSONObject,
        val rules: JSONArray,
    )

    fun compile(
        targetSets: List<TargetSet>,
        conditionsByTargetSet: Map<String, List<TargetCondition>>,
        ruleSets: List<RuleSet>,
        outboundByTargetSet: Map<String, String>,
        profileDirectory: File,
    ): Output {
        val enabledSets = targetSets.filter { it.enabled }.sortedWith(
            compareBy<TargetSet> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.name }
        )
        val enabledRuleSets = ruleSets.filter { it.enabled }.associateBy { it.id }
        val enabledIds = enabledSets.map { it.id }.toSet()
        require(outboundByTargetSet.keys == enabledIds) {
            "代理服务绑定必须且只能覆盖所有启用的目标集合"
        }
        val providers = JSONObject()
        val rules = JSONArray()
        val providerNames = mutableMapOf<String, String>()

        fun providerName(ruleSet: RuleSet): String =
            "CZ-RS-" + ruleSet.id.replace("-", "").take(20)

        for (targetSet in enabledSets) {
            val outbound = outboundByTargetSet[targetSet.id]?.trim()
                ?: error("目标集合“${targetSet.name}”尚未绑定代理服务")
            require(outbound.isNotEmpty()) { "目标集合“${targetSet.name}”绑定的出口名称为空" }

            val conditions = conditionsByTargetSet[targetSet.id].orEmpty()
            val validation = TargetSetValidator.validate(targetSet, conditions, ruleSets)
            require(validation == null) { "目标集合“${targetSet.name}”：$validation" }
            require(conditions.map { it.conditionGroup }.distinct().size == 1) {
                "目标集合“${targetSet.name}”暂不支持多个条件组，请先合并为一个 AND/OR 规则组"
            }
            val groupMode = conditions.first().groupMode
            require(conditions.all { it.groupMode == groupMode }) {
                "目标集合“${targetSet.name}”的条件组混用了 AND / OR"
            }
            val matchAll = targetSet.matchMode == TargetSet.MATCH_ALL
            require((groupMode == TargetCondition.GROUP_ALL) == matchAll) {
                "目标集合“${targetSet.name}”的规则关系与条件组关系不一致"
            }

            val expressions = conditions.map { condition ->
                val expression = when (condition.matcherType) {
                    PolicyCondition.DOMAIN -> "DOMAIN,${condition.matcherValue.trim()}"
                    PolicyCondition.DOMAIN_SUFFIX -> "DOMAIN-SUFFIX,${condition.matcherValue.trim().removePrefix(".")}"
                    PolicyCondition.DOMAIN_KEYWORD -> "DOMAIN-KEYWORD,${condition.matcherValue.trim()}"
                    PolicyCondition.IP -> {
                        val address = condition.matcherValue.trim()
                        val prefix = if (address.contains('/')) address else if (address.contains(':')) "$address/128" else "$address/32"
                        if (address.contains(':')) "IP-CIDR6,$prefix" else "IP-CIDR,$prefix"
                    }
                    PolicyCondition.IP_CIDR -> {
                        val cidr = condition.matcherValue.trim()
                        if (cidr.substringBefore('/').contains(':')) "IP-CIDR6,$cidr" else "IP-CIDR,$cidr"
                    }
                    PolicyCondition.GEOIP -> "GEOIP,${condition.matcherValue.trim().lowercase(Locale.ROOT)}"
                    PolicyCondition.GEOSITE -> "GEOSITE,${condition.matcherValue.trim().lowercase(Locale.ROOT)}"
                    PolicyCondition.IP_SET, PolicyCondition.DOMAIN_SET -> {
                        val ruleSet = enabledRuleSets[condition.matcherValue]
                            ?: error("目标集合“${targetSet.name}”引用了不存在或已停用的规则集")
                        val expectedKind = if (condition.matcherType == PolicyCondition.IP_SET) RuleSet.KIND_IP else RuleSet.KIND_DOMAIN
                        require(ruleSet.kind == expectedKind) { "规则集“${ruleSet.name}”类型不匹配" }
                        val name = providerName(ruleSet)
                        providerNames[ruleSet.id] = name
                        "RULE-SET,$name"
                    }
                    else -> error("不支持的匹配类型：${condition.matcherType}")
                }
                if (condition.negated) "NOT,(($expression))" else expression
            }

            if (matchAll) {
                rules.put("AND,((" + expressions.joinToString("),(") + ")),$outbound")
            } else {
                expressions.forEach { rules.put("$it,$outbound") }
            }
        }

        require(providerNames.values.distinct().size == providerNames.size) {
            "规则集生成的 Mihomo provider 名称发生冲突"
        }
        for ((ruleSetId, name) in providerNames) {
            val ruleSet = enabledRuleSets[ruleSetId] ?: error("规则集不存在：$ruleSetId")
            val provider = JSONObject()
                .put("type", if (ruleSet.sourceType == RuleSet.SOURCE_URL) "http" else "file")
                .put("behavior", if (ruleSet.kind == RuleSet.KIND_IP) "ipcidr" else "domain")
                .put("format", when (ruleSet.format) {
                    RuleSet.FORMAT_MRS -> "mrs"
                    RuleSet.FORMAT_TEXT -> "text"
                    RuleSet.FORMAT_CLASSICAL -> "yaml"
                    else -> error("规则集“${ruleSet.name}”格式不支持")
                })
                .put("interval", (ruleSet.updateInterval / 1000L).coerceAtLeast(3600L))
            when (ruleSet.sourceType) {
                RuleSet.SOURCE_URL -> {
                    val url = ruleSet.sourceValue.trim()
                    require(url.startsWith("https://", true) || url.startsWith("http://", true)) {
                        "规则集“${ruleSet.name}”必须使用 HTTP(S) URL"
                    }
                    val cacheFile = File(profileDirectory, "rule-providers/$name")
                    require(cacheFile.parentFile?.mkdirs() == true || cacheFile.parentFile?.isDirectory == true) {
                        "无法创建规则集缓存目录：${cacheFile.parentFile}"
                    }
                    provider.put("url", url).put("path", cacheFile.absolutePath)
                }
                RuleSet.SOURCE_FILE -> {
                    val source = File(ruleSet.sourceValue.trim())
                    require(source.isAbsolute && source.isFile && source.canRead()) {
                        "规则集“${ruleSet.name}”本地文件不存在或不可读取"
                    }
                    provider.put("path", source.absolutePath)
                }
                else -> error("规则集“${ruleSet.name}”使用了尚不支持的来源类型")
            }
            providers.put(name, provider)
        }
        return Output(providers, rules)
    }
}
