package com.github.kr328.clash.service.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RuleSetDao {
    @Query("SELECT * FROM rule_sets ORDER BY kind, name")
    suspend fun queryAll(): List<RuleSet>

    @Query("SELECT * FROM rule_sets WHERE id = :id")
    suspend fun queryById(id: String): RuleSet?

    @Query("SELECT * FROM rule_sets WHERE kind = :kind ORDER BY name")
    suspend fun queryByKind(kind: String): List<RuleSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ruleSet: RuleSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(ruleSets: List<RuleSet>)

    @Query("UPDATE rule_sets SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM rule_sets WHERE id = :id")
    suspend fun delete(id: String)
}
