package com.github.kr328.clash.service.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A selectable proxy strategy composed from node groups and/or other strategies. */
@Entity(
    tableName = "strategy_groups",
    indices = [Index(value = ["sortOrder"]), Index(value = ["enabled"])]
)
data class StrategyGroup(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "mode") val mode: String = MODE_SELECT,
    @ColumnInfo(name = "iconMode") val iconMode: String = ICON_AUTO,
    @ColumnInfo(name = "iconType") val iconType: String? = null,
    @ColumnInfo(name = "iconValue") val iconValue: String? = null,
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val MODE_SELECT = "SELECT"
        const val MODE_URL_TEST = "URL_TEST"
        const val MODE_FALLBACK = "FALLBACK"
        const val MODE_LOAD_BALANCE = "LOAD_BALANCE"

        const val ICON_AUTO = "AUTO"
        const val ICON_MANUAL = "MANUAL"
    }
}

/**
 * Ordered references inside a strategy group.
 * memberType is NODE_GROUP or STRATEGY_GROUP; memberId is the referenced id.
 * Polymorphic references are validated by StrategyGraphValidator.
 */
@Entity(
    tableName = "strategy_group_members",
    primaryKeys = ["groupId", "memberType", "memberId"],
    indices = [Index(value = ["groupId", "position"]), Index(value = ["memberType", "memberId"])]
)
data class StrategyGroupMember(
    @ColumnInfo(name = "groupId") val groupId: String,
    @ColumnInfo(name = "memberType") val memberType: String,
    @ColumnInfo(name = "memberId") val memberId: String,
    @ColumnInfo(name = "position") val position: Int,
) {
    companion object {
        const val TYPE_NODE_GROUP = "NODE_GROUP"
        const val TYPE_STRATEGY_GROUP = "STRATEGY_GROUP"
    }
}
