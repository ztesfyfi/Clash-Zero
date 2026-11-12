package com.github.kr328.clash.service.data

/**
 * Shared icon representation for node groups, strategy groups and policies.
 *
 * EMOJI stores a Unicode emoji (including country flags).
 * BRAND stores a stable brand key resolved by the app's bundled brand icon set.
 * ASSET stores an app-owned icon asset key.
 */
data class IconSpec(
    val type: String,
    val value: String,
) {
    companion object {
        const val TYPE_EMOJI = "EMOJI"
        const val TYPE_BRAND = "BRAND"
        const val TYPE_ASSET = "ASSET"
    }
}

/**
 * Converts ISO 3166-1 alpha-2 country/region codes into Unicode flag emoji.
 * Returns null for unknown or non-alpha-2 values.
 */
object RegionFlag {
    fun fromRegionCode(regionCode: String): String? {
        val code = regionCode.trim().uppercase()
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return null

        val first = String(Character.toChars(0x1F1E6 + (code[0] - 'A')))
        val second = String(Character.toChars(0x1F1E6 + (code[1] - 'A')))
        return first + second
    }
}
