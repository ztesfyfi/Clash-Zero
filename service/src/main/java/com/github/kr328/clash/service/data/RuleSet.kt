package com.github.kr328.clash.service.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A reusable IP or domain collection referenced by routing-policy conditions. */
@Entity(
    tableName = "rule_sets",
    indices = [Index(value = ["kind"]), Index(value = ["sourceType"])]
)
data class RuleSet(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "sourceType") val sourceType: String,
    @ColumnInfo(name = "sourceValue") val sourceValue: String,
    @ColumnInfo(name = "format") val format: String = FORMAT_MRS,
    @ColumnInfo(name = "updateInterval") val updateInterval: Long = 86400000L,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val KIND_IP = "IP"
        const val KIND_DOMAIN = "DOMAIN"

        const val SOURCE_BUILTIN = "BUILTIN"
        const val SOURCE_URL = "URL"
        const val SOURCE_FILE = "FILE"

        const val FORMAT_MRS = "MRS"
        const val FORMAT_CLASSICAL = "CLASSICAL"
        const val FORMAT_TEXT = "TEXT"
    }
}

/** Common built-in GeoIP/GeoSite tags presented by the visual rule picker. */
object GeoRuleCatalog {
    data class Entry(
        val id: String,
        val name: String,
        val matcherType: String,
        val value: String,
    )

    val geoIp: List<Entry> = listOf(
        Entry("geoip-cn", "中国大陆 IP", PolicyCondition.GEOIP, "cn"),
        Entry("geoip-hk", "香港 IP", PolicyCondition.GEOIP, "hk"),
        Entry("geoip-tw", "台湾 IP", PolicyCondition.GEOIP, "tw"),
        Entry("geoip-jp", "日本 IP", PolicyCondition.GEOIP, "jp"),
        Entry("geoip-us", "美国 IP", PolicyCondition.GEOIP, "us"),
        Entry("geoip-sg", "新加坡 IP", PolicyCondition.GEOIP, "sg"),
        Entry("geoip-kr", "韩国 IP", PolicyCondition.GEOIP, "kr"),
        Entry("geoip-gb", "英国 IP", PolicyCondition.GEOIP, "gb"),
        Entry("geoip-de", "德国 IP", PolicyCondition.GEOIP, "de"),
        Entry("geoip-fr", "法国 IP", PolicyCondition.GEOIP, "fr"),
        Entry("geoip-ca", "加拿大 IP", PolicyCondition.GEOIP, "ca"),
        Entry("geoip-au", "澳大利亚 IP", PolicyCondition.GEOIP, "au"),
    )

    val geoSite: List<Entry> = listOf(
        Entry("geosite-cn", "大陆网站集", PolicyCondition.GEOSITE, "cn"),
        Entry("geosite-youtube", "YouTube 网站集", PolicyCondition.GEOSITE, "youtube"),
        Entry("geosite-google", "Google 网站集", PolicyCondition.GEOSITE, "google"),
        Entry("geosite-openai", "OpenAI 网站集", PolicyCondition.GEOSITE, "openai"),
        Entry("geosite-gemini", "Gemini 网站集", PolicyCondition.GEOSITE, "google-gemini"),
        Entry("geosite-claude", "Claude 网站集", PolicyCondition.GEOSITE, "anthropic"),
        Entry("geosite-netflix", "Netflix 网站集", PolicyCondition.GEOSITE, "netflix"),
        Entry("geosite-disney", "Disney+ 网站集", PolicyCondition.GEOSITE, "disney"),
        Entry("geosite-telegram", "Telegram 网站集", PolicyCondition.GEOSITE, "telegram"),
        Entry("geosite-games", "游戏网站集", PolicyCondition.GEOSITE, "category-games"),
        Entry("geosite-ads", "广告域名集", PolicyCondition.GEOSITE, "category-ads-all"),
    )
}
