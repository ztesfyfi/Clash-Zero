package com.github.kr328.clash.service.data

import androidx.room.withTransaction

/** Transactional persistence boundary for target collections. */
class TargetSetRepository(
    private val database: Database = Database.database,
) {
    suspend fun save(targetSet: TargetSet, conditions: List<TargetCondition>) {
        require(conditions.all { it.targetSetId == targetSet.id }) {
            "Every condition must belong to the target set being saved"
        }
        require(conditions.map { it.id }.distinct().size == conditions.size) {
            "目标集合中不能存在重复规则"
        }
        val sets = database.openRuleSetDao().queryAll()
        val error = TargetSetValidator.validate(targetSet, conditions, sets)
        require(error == null) { error ?: "目标集合配置无效" }

        val duplicateName = database.openTargetSetDao().queryAll().any {
            it.id != targetSet.id && it.name.equals(targetSet.name.trim(), ignoreCase = true)
        }
        require(!duplicateName) { "目标集合名称已存在：${targetSet.name}" }

        database.withTransaction {
            val dao = database.openTargetSetDao()
            dao.upsert(targetSet.copy(name = targetSet.name.trim(), updatedAt = System.currentTimeMillis()))
            dao.replaceConditions(targetSet.id, conditions)
        }
    }

    suspend fun reorder(orderedIds: List<String>) {
        val dao = database.openTargetSetDao()
        val existingIds = dao.queryAll().map { it.id }.toSet()
        require(orderedIds.size == existingIds.size && orderedIds.toSet() == existingIds) {
            "目标集合排序必须且只能包含所有现有集合一次"
        }
        database.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                val current = dao.queryById(id) ?: error("目标集合不存在：$id")
                dao.upsert(current.copy(sortOrder = index * 10, updatedAt = System.currentTimeMillis()))
            }
        }
    }
}
