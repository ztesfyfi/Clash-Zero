package com.github.kr328.clash.service.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Update
import java.util.UUID

@Dao
@TypeConverters(Converters::class)
interface NodeGroupDao {
    @Query("SELECT * FROM node_groups ORDER BY createdAt, name")
    suspend fun queryAll(): List<NodeGroup>

    @Query("SELECT * FROM node_groups WHERE id = :id")
    suspend fun queryById(id: String): NodeGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: NodeGroup)

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

    @Query("DELETE FROM node_group_members WHERE profileUuid = :profileUuid")
    suspend fun removeMembersForProfile(profileUuid: UUID)
}
