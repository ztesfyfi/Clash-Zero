package com.github.kr328.clash.service.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Transaction
import java.util.UUID

@Dao
@TypeConverters(Converters::class)
interface NodeGroupDao {
    @Query("SELECT * FROM node_groups ORDER BY createdAt, name")
    suspend fun queryAll(): List<NodeGroup>

    @Query("SELECT * FROM node_groups WHERE id = :id")
    suspend fun queryById(id: String): NodeGroup?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: NodeGroup)

    @Update
    suspend fun update(group: NodeGroup)

    @Transaction
    suspend fun upsert(group: NodeGroup) {
        if (queryById(group.id) == null) insert(group) else update(group)
    }

    @Query("DELETE FROM node_groups WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(member: NodeGroupMember)

    @Query("SELECT * FROM node_group_members WHERE groupId = :groupId ORDER BY proxyName")
    suspend fun queryMembers(groupId: String): List<NodeGroupMember>

    @Query("DELETE FROM node_group_members WHERE groupId = :groupId AND profileUuid = :profileUuid AND proxyKey = :proxyKey")
    suspend fun removeMember(groupId: String, profileUuid: UUID, proxyKey: String)

    @Query("DELETE FROM node_group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: String)

    @Transaction
    suspend fun replaceMembers(groupId: String, members: List<NodeGroupMember>) {
        clearMembers(groupId)
        members.distinctBy { "${it.profileUuid}:${it.proxyKey}" }
            .forEach { addMember(it.copy(groupId = groupId)) }
    }

    /**
     * Replaces the visible selection while retaining members that disappeared
     * from a successfully parsed subscription. Those stale entries can then be
     * shown and removed explicitly instead of being silently discarded.
     *
     * availableKeys contains profileUuid:proxyKey pairs from subscriptions
     * whose current proxy list was parsed successfully.
     */
    @Transaction
    suspend fun replaceMembersPreservingMissing(
        groupId: String,
        members: List<NodeGroupMember>,
        availableKeys: Set<String>
    ) {
        val existing = queryMembers(groupId)
        val stale = existing.filter { "${it.profileUuid}:${it.proxyKey}" !in availableKeys }
        clearMembers(groupId)
        (members + stale)
            .distinctBy { "${it.profileUuid}:${it.proxyKey}" }
            .forEach { addMember(it.copy(groupId = groupId)) }
    }

    @Query("DELETE FROM node_group_members WHERE profileUuid = :profileUuid")
    suspend fun removeMembersForProfile(profileUuid: UUID)
}
