package com.github.kr328.clash.service.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "profile_node_groups", indices = [Index(value = ["sortOrder"])])
data class ProfileNodeGroup(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "isPreset") val isPreset: Boolean,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "sortOrder") val sortOrder: Int = 0,
)

@Entity(
    tableName = "profile_node_overrides",
    primaryKeys = ["profileUuid", "nodeKey"],
    indices = [Index(value = ["groupId"])],
)
data class ProfileNodeOverride(
    @ColumnInfo(name = "profileUuid") val profileUuid: UUID,
    @ColumnInfo(name = "nodeKey") val nodeKey: String,
    @ColumnInfo(name = "originalName") val originalName: String,
    @ColumnInfo(name = "customName") val customName: String? = null,
    @ColumnInfo(name = "groupId") val groupId: String? = null,
)
