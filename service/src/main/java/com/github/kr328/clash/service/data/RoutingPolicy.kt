package com.github.kr328.clash.service.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Ordered traffic-matching policy. Lower priority values are evaluated first. */
@Entity(
    tableName = "routing_policies",
    indices = [Index(value = ["priority"]), Index(value = ["enabled"])]
)
data class RoutingPolicy(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "targetType") val targetType: String,
    @ColumnInfo(name = "targetId") val targetId: String? = null,
    @ColumnInfo(name = "matchMode") val matchMode: String = MATCH_ANY,
    @ColumnInfo(name = "iconMode") val iconMode: String = StrategyGroup.ICON_AUTO,
    @ColumnInfo(name = "iconType") val iconType: String? = null,
    @ColumnInfo(name = "iconValue") val iconValue: String? = null,
    @ColumnInfo(name = "priority") val priority: Int = 0,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis(),
) {
    fun resolvedIcon(): IconSpec? {
        if (iconMode == StrategyGroup.ICON_MANUAL) {
            return if (!iconType.isNullOrBlank() && !iconValue.isNullOrBlank()) {
                IconSpec(iconType, iconValue)
            } else {
                null
            }
        }
        return IconAutoMatcher.match(name)
    }

    companion object {
        const val TARGET_STRATEGY_GROUP = "STRATEGY_GROUP"
        const val TARGET_DIRECT = "DIRECT"
        const val TARGET_REJECT = "REJECT"
        const val MATCH_ALL = "ALL"
        const val MATCH_ANY = "ANY"
    }
}

/**
 * A policy condition. Conditions sharing conditionGroup are combined using
 * groupMode (ALL/ANY); negated conditions implement NOT without raw YAML.
 */
@Entity(
    tableName = "policy_conditions",
    indices = [Index(value = ["policyId", "conditionGroup"])],
    foreignKeys = [
        ForeignKey(
            entity = RoutingPolicy::class,
            parentColumns = ["id"],
            childColumns = ["policyId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PolicyCondition(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "policyId") val policyId: String,
    @ColumnInfo(name = "conditionGroup") val conditionGroup: String = "default",
    @ColumnInfo(name = "groupMode") val groupMode: String = GROUP_ALL,
    @ColumnInfo(name = "matcherType") val matcherType: String,
    @ColumnInfo(name = "matcherValue") val matcherValue: String,
    @ColumnInfo(name = "negated") val negated: Boolean = false,
) {
    companion object {
        const val GROUP_ALL = "ALL"
        const val GROUP_ANY = "ANY"

        const val DOMAIN = "DOMAIN"
        const val DOMAIN_SUFFIX = "DOMAIN_SUFFIX"
        const val DOMAIN_KEYWORD = "DOMAIN_KEYWORD"
        const val IP = "IP"
        const val IP_CIDR = "IP_CIDR"
        const val GEOIP = "GEOIP"
        const val GEOSITE = "GEOSITE"
        const val IP_SET = "IP_SET"
        const val DOMAIN_SET = "DOMAIN_SET"
    }
}
