package com.github.kr328.clash.service.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
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
@TypeConverters(Converters::class)
data class ProfileNodeOverride(
    @ColumnInfo(name = "profileUuid") val profileUuid: UUID,
    @ColumnInfo(name = "nodeKey") val nodeKey: String,
    @ColumnInfo(name = "originalName") val originalName: String,
    @ColumnInfo(name = "customName") val customName: String? = null,
    @ColumnInfo(name = "groupId") val groupId: String? = null,
    @ColumnInfo(name = "delayMs") val delayMs: Int? = null,
    @ColumnInfo(name = "testedAt") val testedAt: Long? = null,
    @ColumnInfo(name = "testError") val testError: String? = null,
)

@Entity(tableName = "profile_node_settings")
data class ProfileNodeSettings(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = 1,
    @ColumnInfo(name = "goodThresholdMs") val goodThresholdMs: Int? = null,
    @ColumnInfo(name = "slowThresholdMs") val slowThresholdMs: Int? = null,
    @ColumnInfo(name = "presetsInitialized", defaultValue = "0") val presetsInitialized: Boolean = false,
)
