package com.github.kr328.clash.service.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import java.util.UUID

@Dao
@TypeConverters(Converters::class)
interface ProfileNodeDao {
    @Query("SELECT * FROM profile_node_groups ORDER BY sortOrder, name")
    suspend fun queryGroups(): List<ProfileNodeGroup>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroups(groups: List<ProfileNodeGroup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: ProfileNodeGroup)

    @Query("DELETE FROM profile_node_groups WHERE id = :id AND isPreset = 0")
    suspend fun deleteCustomGroup(id: String)

    @Query("UPDATE profile_node_groups SET enabled = :enabled WHERE id = :id")
    suspend fun setGroupEnabled(id: String, enabled: Boolean)

    @Query("SELECT * FROM profile_node_overrides WHERE profileUuid = :profileUuid")
    suspend fun queryOverrides(profileUuid: UUID): List<ProfileNodeOverride>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: ProfileNodeOverride)

    @Query("DELETE FROM profile_node_overrides WHERE profileUuid = :profileUuid AND nodeKey = :nodeKey")
    suspend fun deleteOverride(profileUuid: UUID, nodeKey: String)

    @Query("DELETE FROM profile_node_overrides WHERE profileUuid = :profileUuid")
    suspend fun deleteProfileOverrides(profileUuid: UUID)
}
