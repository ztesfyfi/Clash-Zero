package com.github.kr328.clash.service.data

/**
 * First-run editable presets. They are inserted only into empty tables and
 * remain ordinary user-editable records after installation.
 */
object DefaultRoutingPreset {
    private const val GROUP_AI = "preset-group-ai"
    private const val GROUP_YOUTUBE = "preset-group-youtube"
    private const val GROUP_STREAMING = "preset-group-streaming"
    private const val GROUP_TELEGRAM = "preset-group-telegram"
    private const val GROUP_SOCIAL = "preset-group-social"
    private const val GROUP_GAMES = "preset-group-games"
    private const val GROUP_GLOBAL = "preset-group-global"
    private const val GROUP_DIRECT = "preset-group-direct"
    private const val GROUP_ADS = "preset-group-ads"
    private const val GROUP_AUTO = "preset-group-auto"
    private const val GROUP_FALLBACK = "preset-group-fallback"
    private const val GROUP_BALANCE = "preset-group-balance"

    suspend fun installIfEmpty(database: Database) {
        val strategyDao = database.openStrategyGroupDao()
        val policyDao = database.openRoutingPolicyDao()

        if (strategyDao.queryAll().isEmpty()) {
            strategyDao.upsertAll(
                listOf(
                    group(GROUP_AI, "AI 服务", "🤖", 0),
                    group(GROUP_YOUTUBE, "YouTube", "▶️", 1),
                    group(GROUP_STREAMING, "流媒体", "🎬", 2),
                    group(GROUP_TELEGRAM, "Telegram", "telegram", 3, IconSpec.TYPE_BRAND),
                    group(GROUP_SOCIAL, "社交媒体", "💬", 4),
                    group(GROUP_GAMES, "游戏", "🎮", 5),
                    group(GROUP_GLOBAL, "国外网站", "🌐", 6),
                    group(GROUP_DIRECT, "国内直连", "🇨🇳", 7),
                    group(GROUP_ADS, "广告拦截", "🚫", 8),
                    group(GROUP_AUTO, "自动选择", "⚡", 9, mode = StrategyGroup.MODE_URL_TEST),
                    group(GROUP_FALLBACK, "故障转移", "🔄", 10, mode = StrategyGroup.MODE_FALLBACK),
                    group(GROUP_BALANCE, "负载均衡", "⚖️", 11, mode = StrategyGroup.MODE_LOAD_BALANCE),
                )
            )
        }

        if (policyDao.queryAll().isEmpty()) {
            val policies = listOf(
                policy("preset-policy-lan", "局域网直连", RoutingPolicy.TARGET_DIRECT, null, 0),
                policy("preset-policy-ai", "AI 服务", RoutingPolicy.TARGET_STRATEGY_GROUP, GROUP_AI, 10),
                policy("preset-policy-youtube", "YouTube", RoutingPolicy.TARGET_STRATEGY_GROUP, GROUP_YOUTUBE, 20),
                policy("preset-policy-streaming", "流媒体", RoutingPolicy.TARGET_STRATEGY_GROUP, GROUP_STREAMING, 30),
                policy("preset-policy-telegram", "Telegram", RoutingPolicy.TARGET_STRATEGY_GROUP, GROUP_TELEGRAM, 40),
                policy("preset-policy-social", "社交媒体", RoutingPolicy.TARGET_STRATEGY_GROUP, GROUP_SOCIAL, 50),
                policy("preset-policy-games", "游戏", RoutingPolicy.TARGET_STRATEGY_GROUP, GROUP_GAMES, 60),
                policy("preset-policy-ads", "广告拦截", RoutingPolicy.TARGET_REJECT, null, 70),
                policy("preset-policy-cn-domain", "大陆网站直连", RoutingPolicy.TARGET_DIRECT, null, 80),
                policy("preset-policy-cn-ip", "大陆 IP 直连", RoutingPolicy.TARGET_DIRECT, null, 90),
                policy("preset-policy-global", "其他流量", RoutingPolicy.TARGET_STRATEGY_GROUP, GROUP_GLOBAL, 100),
            )
            policyDao.upsertAll(policies)

            val conditions = mutableListOf<PolicyCondition>()
            fun add(policyId: String, matcher: String, value: String) {
                conditions += PolicyCondition(
                    policyId = policyId,
                    conditionGroup = "default",
                    groupMode = PolicyCondition.GROUP_ANY,
                    matcherType = matcher,
                    matcherValue = value,
                )
            }

            listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "::1/128", "fc00::/7", "fe80::/10")
                .forEach { add("preset-policy-lan", PolicyCondition.IP_CIDR, it) }
            listOf("openai", "google-gemini", "anthropic")
                .forEach { add("preset-policy-ai", PolicyCondition.GEOSITE, it) }
            add("preset-policy-youtube", PolicyCondition.GEOSITE, "youtube")
            listOf("netflix", "disney")
                .forEach { add("preset-policy-streaming", PolicyCondition.GEOSITE, it) }
            add("preset-policy-telegram", PolicyCondition.GEOSITE, "telegram")
            listOf("facebook", "instagram", "twitter", "discord")
                .forEach { add("preset-policy-social", PolicyCondition.GEOSITE, it) }
            add("preset-policy-games", PolicyCondition.GEOSITE, "category-games")
            add("preset-policy-ads", PolicyCondition.GEOSITE, "category-ads")
            add("preset-policy-cn-domain", PolicyCondition.GEOSITE, "cn")
            add("preset-policy-cn-ip", PolicyCondition.GEOIP, "cn")

            policyDao.upsertConditions(conditions)
        }
    }

    private fun group(
        id: String,
        name: String,
        icon: String,
        order: Int,
        iconType: String = IconSpec.TYPE_EMOJI,
        mode: String = StrategyGroup.MODE_SELECT,
    ) = StrategyGroup(
        id = id,
        name = name,
        mode = mode,
        iconMode = StrategyGroup.ICON_MANUAL,
        iconType = iconType,
        iconValue = icon,
        sortOrder = order,
    )

    private fun policy(
        id: String,
        name: String,
        targetType: String,
        targetId: String?,
        priority: Int,
    ) = RoutingPolicy(
        id = id,
        name = name,
        targetType = targetType,
        targetId = targetId,
        matchMode = RoutingPolicy.MATCH_ANY,
        priority = priority,
    )
}
