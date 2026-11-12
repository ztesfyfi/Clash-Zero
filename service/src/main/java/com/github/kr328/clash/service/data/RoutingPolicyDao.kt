package com.github.kr328.clash.service.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RoutingPolicyDao {
    @Query("SELECT * FROM routing_policies ORDER BY priority, createdAt, name")
    suspend fun queryAll(): List<RoutingPolicy>

    @Query("SELECT * FROM routing_policies WHERE id = :id")
    suspend fun queryById(id: String): RoutingPolicy?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(policy: RoutingPolicy)

    @Query("DELETE FROM routing_policies WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM policy_conditions WHERE policyId = :policyId ORDER BY conditionGroup, id")
    suspend fun queryConditions(policyId: String): List<PolicyCondition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCondition(condition: PolicyCondition)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConditions(conditions: List<PolicyCondition>)

    @Query("DELETE FROM policy_conditions WHERE id = :id")
    suspend fun deleteCondition(id: String)

    @Query("DELETE FROM policy_conditions WHERE policyId = :policyId")
    suspend fun clearConditions(policyId: String)

    @Query("UPDATE routing_policies SET priority = :priority, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE routing_policies SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())
}
