package com.github.kr328.clash.service.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A named collection of traffic-matching conditions. It has no outbound target. */
@Entity(
    tableName = "target_sets",
    indices = [Index(value = ["sortOrder"]), Index(value = ["enabled"])]
)
data class TargetSet(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "matchMode") val matchMode: String = MATCH_ANY,
    @ColumnInfo(name = "iconMode") val iconMode: String = StrategyGroup.ICON_AUTO,
    @ColumnInfo(name = "iconType") val iconType: String? = null,
    @ColumnInfo(name = "iconValue") val iconValue: String? = null,
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val MATCH_ALL = "ALL"
        const val MATCH_ANY = "ANY"
    }
}

/** Conditions belonging to a target set; no proxy or node-group reference is stored here. */
@Entity(
    tableName = "target_conditions",
    indices = [Index(value = ["targetSetId", "conditionGroup"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetSet::class,
            parentColumns = ["id"],
            childColumns = ["targetSetId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TargetCondition(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "targetSetId") val targetSetId: String,
    @ColumnInfo(name = "conditionGroup") val conditionGroup: String = "default",
    @ColumnInfo(name = "groupMode") val groupMode: String = GROUP_ALL,
    @ColumnInfo(name = "matcherType") val matcherType: String,
    @ColumnInfo(name = "matcherValue") val matcherValue: String,
    @ColumnInfo(name = "negated") val negated: Boolean = false,
) {
    companion object {
        const val GROUP_ALL = "ALL"
        const val GROUP_ANY = "ANY"
    }
}
