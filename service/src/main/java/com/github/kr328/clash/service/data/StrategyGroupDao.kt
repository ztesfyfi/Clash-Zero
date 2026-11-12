package com.github.kr328.clash.service.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface StrategyGroupDao {
    @Query("SELECT * FROM strategy_groups ORDER BY sortOrder, createdAt, name")
    suspend fun queryAll(): List<StrategyGroup>

    @Query("SELECT * FROM strategy_groups WHERE id = :id")
    suspend fun queryById(id: String): StrategyGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: StrategyGroup)

    @Query("DELETE FROM strategy_groups WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM strategy_group_members WHERE groupId = :groupId ORDER BY position")
    suspend fun queryMembers(groupId: String): List<StrategyGroupMember>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(member: StrategyGroupMember)

    @Query("DELETE FROM strategy_group_members WHERE groupId = :groupId AND memberType = :memberType AND memberId = :memberId")
    suspend fun removeMember(groupId: String, memberType: String, memberId: String)

    @Query("DELETE FROM strategy_group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: String)

    @Query("UPDATE strategy_group_members SET position = :position WHERE groupId = :groupId AND memberType = :memberType AND memberId = :memberId")
    suspend fun updateMemberPosition(groupId: String, memberType: String, memberId: String, position: Int)

    @Query("SELECT * FROM strategy_group_members WHERE memberType = :memberType AND memberId = :memberId")
    suspend fun queryParents(memberType: String, memberId: String): List<StrategyGroupMember>
}
