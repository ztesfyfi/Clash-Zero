package com.github.kr328.clash.service.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TargetSetDao {
    @Query("SELECT * FROM target_sets ORDER BY sortOrder, createdAt, name")
    suspend fun queryAll(): List<TargetSet>

    @Query("SELECT * FROM target_sets WHERE id = :id")
    suspend fun queryById(id: String): TargetSet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(targetSet: TargetSet)

    @Query("UPDATE target_sets SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM target_sets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM target_conditions WHERE targetSetId = :targetSetId ORDER BY conditionGroup, id")
    suspend fun queryConditions(targetSetId: String): List<TargetCondition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConditions(conditions: List<TargetCondition>)

    @Query("DELETE FROM target_conditions WHERE targetSetId = :targetSetId")
    suspend fun clearConditions(targetSetId: String)

    @Transaction
    suspend fun replaceConditions(targetSetId: String, conditions: List<TargetCondition>) {
        require(conditions.all { it.targetSetId == targetSetId }) {
            "Every condition must belong to the target set being saved"
        }
        clearConditions(targetSetId)
        upsertConditions(conditions)
    }
}
