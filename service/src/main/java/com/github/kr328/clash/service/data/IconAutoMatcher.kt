package com.github.kr328.clash.service.data

/**
 * Localized, deterministic name-to-icon matching. Manual selections are kept
 * separately by each entity's iconMode and are never replaced by this matcher.
 */
object IconAutoMatcher {
    private data class Entry(
        val keys: List<String>,
        val type: String,
        val value: String,
    )

    private val entries = listOf(
        Entry(listOf("openai", "chatgpt", "gpt", "人工智能助手"), IconSpec.TYPE_BRAND, "openai"),
        Entry(listOf("gemini", "谷歌ai"), IconSpec.TYPE_BRAND, "google-gemini"),
        Entry(listOf("claude", "anthropic"), IconSpec.TYPE_BRAND, "claude"),
        Entry(listOf("youtube", "油管"), IconSpec.TYPE_BRAND, "youtube"),
        Entry(listOf("netflix", "奈飞"), IconSpec.TYPE_BRAND, "netflix"),
        Entry(listOf("disney+", "disney plus", "迪士尼"), IconSpec.TYPE_BRAND, "disneyplus"),
        Entry(listOf("telegram", "电报"), IconSpec.TYPE_BRAND, "telegram"),
        Entry(listOf("whatsapp", "瓦次普"), IconSpec.TYPE_BRAND, "whatsapp"),
        Entry(listOf("discord"), IconSpec.TYPE_BRAND, "discord"),
        Entry(listOf("github"), IconSpec.TYPE_BRAND, "github"),
        Entry(listOf("gitlab"), IconSpec.TYPE_BRAND, "gitlab"),
        Entry(listOf("cloudflare"), IconSpec.TYPE_BRAND, "cloudflare"),
        Entry(listOf("ai", "人工智能"), IconSpec.TYPE_EMOJI, "🤖"),
        Entry(listOf("game", "游戏", "steam"), IconSpec.TYPE_EMOJI, "🎮"),
        Entry(listOf("media", "streaming", "流媒体", "影音"), IconSpec.TYPE_EMOJI, "🎬"),
        Entry(listOf("auto", "自动选择", "自动测速"), IconSpec.TYPE_EMOJI, "⚡"),
        Entry(listOf("fallback", "故障转移"), IconSpec.TYPE_EMOJI, "🔄"),
        Entry(listOf("balance", "负载均衡"), IconSpec.TYPE_EMOJI, "⚖️"),
        Entry(listOf("大陆", "国内"), IconSpec.TYPE_EMOJI, "🇨🇳"),
        Entry(listOf("direct", "直连"), IconSpec.TYPE_EMOJI, "🏠"),
        Entry(listOf("reject", "拦截", "广告"), IconSpec.TYPE_EMOJI, "🚫"),
        Entry(listOf("proxy", "代理", "国外网站"), IconSpec.TYPE_EMOJI, "🌐"),
    )

    fun match(name: String): IconSpec? {
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty()) return null
        val entry = entries.firstOrNull { candidate ->
            candidate.keys.any { key -> normalized.contains(key) }
        } ?: return null
        return IconSpec(entry.type, entry.value)
    }
}
