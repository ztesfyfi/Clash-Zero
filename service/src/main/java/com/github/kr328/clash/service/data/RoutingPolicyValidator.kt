package com.github.kr328.clash.service.data

/** Validates policy targets and references before a policy is saved or compiled. */
object RoutingPolicyValidator {
    fun validate(
        policy: RoutingPolicy,
        conditions: Collection<PolicyCondition>,
        strategyGroupIds: Set<String>,
        ruleSets: Collection<RuleSet>,
    ): String? {
        when (policy.targetType) {
            RoutingPolicy.TARGET_STRATEGY_GROUP -> {
                val target = policy.targetId
                if (target.isNullOrBlank() || target !in strategyGroupIds) {
                    return "Target strategy group does not exist"
                }
            }
            RoutingPolicy.TARGET_DIRECT, RoutingPolicy.TARGET_REJECT -> {
                if (!policy.targetId.isNullOrBlank()) {
                    return "DIRECT/REJECT targets must not reference a strategy group"
                }
            }
            else -> return "Unsupported policy target: ${policy.targetType}"
        }

        if (policy.matchMode != RoutingPolicy.MATCH_ALL && policy.matchMode != RoutingPolicy.MATCH_ANY) {
            return "Unsupported policy match mode: ${policy.matchMode}"
        }

        val setsById = ruleSets.associateBy { it.id }
        for (condition in conditions) {
            if (condition.matcherValue.isBlank()) return "Condition value cannot be empty"
            if (condition.groupMode != PolicyCondition.GROUP_ALL &&
                condition.groupMode != PolicyCondition.GROUP_ANY
            ) {
                return "Unsupported condition group mode: ${condition.groupMode}"
            }

            when (condition.matcherType) {
                PolicyCondition.DOMAIN,
                PolicyCondition.DOMAIN_SUFFIX,
                PolicyCondition.DOMAIN_KEYWORD -> Unit
                PolicyCondition.IP_CIDR -> {
                    if (!condition.matcherValue.contains('/')) {
                        return "IP network must use CIDR notation"
                    }
                }
                PolicyCondition.GEOIP -> {
                    if (condition.matcherValue.length != 2 ||
                        condition.matcherValue.any { it !in 'a'..'z' && it !in 'A'..'Z' }
                    ) return "GeoIP requires a two-letter country code"
                }
                PolicyCondition.GEOSITE -> Unit
                PolicyCondition.IP_SET, PolicyCondition.DOMAIN_SET -> {
                    val set = setsById[condition.matcherValue]
                        ?: return "Referenced rule set does not exist"
                    val expectedKind = if (condition.matcherType == PolicyCondition.IP_SET) {
                        RuleSet.KIND_IP
                    } else {
                        RuleSet.KIND_DOMAIN
                    }
                    if (set.kind != expectedKind) return "Rule set type does not match condition type"
                }
                PolicyCondition.PROCESS_PACKAGE -> Unit
                else -> return "Unsupported condition type: ${condition.matcherType}"
            }
        }
        return null
    }
}
