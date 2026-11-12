package com.github.kr328.clash.service.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.UUID

/**
 * User-defined node group.
 *
 * STATIC groups are populated manually.
 * NAME groups are populated from nodes carrying [nameTag].
 * NAME_REGION groups additionally require a verified exit region.
 */
@Entity(
    tableName = "node_groups",
    indices = [Index(value = ["mode"]), Index(value = ["nameTag"])]
)
data class NodeGroup(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "nameTag") val nameTag: String? = null,
    @ColumnInfo(name = "regionCode") val regionCode: String? = null,
    @ColumnInfo(name = "iconType") val iconType: String? = null,
    @ColumnInfo(name = "iconValue") val iconValue: String? = null,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val MODE_STATIC = "STATIC"
        const val MODE_NAME = "NAME"
        const val MODE_NAME_REGION = "NAME_REGION"
    }

    /** A custom icon wins; otherwise region groups can show their flag. */
    fun resolvedIcon(): IconSpec? {
        if (!iconType.isNullOrBlank() && !iconValue.isNullOrBlank()) {
            return IconSpec(iconType, iconValue)
        }
        val flag = regionCode?.let(RegionFlag::fromRegionCode)
        return flag?.let { IconSpec(IconSpec.TYPE_EMOJI, it) }
    }
}

/**
 * Manual membership for static groups.
 *
 * proxyKey must be derived from the normalized proxy definition, not its
 * mutable display name, so renaming a node does not break the association.
 */
@Entity(
    tableName = "node_group_members",
    primaryKeys = ["groupId", "profileUuid", "proxyKey"],
    indices = [Index(value = ["profileUuid"])],
    foreignKeys = [
        ForeignKey(
            entity = NodeGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Imported::class,
            parentColumns = ["uuid"],
            childColumns = ["profileUuid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(Converters::class)
data class NodeGroupMember(
    @ColumnInfo(name = "groupId") val groupId: String,
    @ColumnInfo(name = "profileUuid") val profileUuid: UUID,
    @ColumnInfo(name = "proxyKey") val proxyKey: String,
    @ColumnInfo(name = "proxyName") val proxyName: String,
)
