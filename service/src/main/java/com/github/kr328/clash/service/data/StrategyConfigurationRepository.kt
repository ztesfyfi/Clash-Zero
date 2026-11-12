package com.github.kr328.clash.service.data

import androidx.room.withTransaction

/**
 * Transactional entry point for the visual strategy editor.
 * It validates references before persisting and keeps group/policy edits atomic.
 */
class StrategyConfigurationRepository(
    private val database: Database = Database.database,
) {
    suspend fun saveStrategyGroup(
        group: StrategyGroup,
        members: List<StrategyGroupMember>,
    ) {
        require(members.all { it.groupId == group.id }) {
            "Every member must belong to the strategy group being saved"
        }

        val existingGroups = database.openStrategyGroupDao().queryAll()
        val allMembers = existingGroups.flatMap {
            database.openStrategyGroupDao().queryMembers(it.id)
        }.filterNot { it.groupId == group.id }

        val references = (allMembers + members).map {
            StrategyGraphValidator.Reference(it.groupId, it.memberType, it.memberId)
        }
        val groupIds = (existingGroups.map { it.id } + group.id).toSet()
        val nodeGroupIds = database.openNodeGroupDao().queryAll().map { it.id }.toSet()
        val error = StrategyGraphValidator.validate(groupIds, nodeGroupIds, references)
        require(error == null) { error ?: "Invalid strategy group" }

        database.withTransaction {
            val dao = database.openStrategyGroupDao()
            dao.upsert(group.copy(updatedAt = System.currentTimeMillis()))
            dao.clearMembers(group.id)
            members.sortedBy { it.position }.forEach { dao.addMember(it) }
        }
    }

    suspend fun savePolicy(
        policy: RoutingPolicy,
        conditions: List<PolicyCondition>,
    ) {
        require(conditions.all { it.policyId == policy.id }) {
            "Every condition must belong to the policy being saved"
        }

        val strategyGroupIds = database.openStrategyGroupDao().queryAll().map { it.id }.toSet()
        val ruleSets = database.openRuleSetDao().queryAll()
        val error = RoutingPolicyValidator.validate(
            policy,
            conditions,
            strategyGroupIds,
            ruleSets,
        )
        require(error == null) { error ?: "Invalid routing policy" }

        database.withTransaction {
            val dao = database.openRoutingPolicyDao()
            dao.upsert(policy.copy(updatedAt = System.currentTimeMillis()))
            dao.clearConditions(policy.id)
            dao.upsertConditions(conditions)
        }
    }

    /** Rewrites priority values in a stable sequence after drag-and-drop. */
    suspend fun reorderPolicies(orderedPolicyIds: List<String>) {
        val dao = database.openRoutingPolicyDao()
        val existingIds = dao.queryAll().map { it.id }.toSet()
        require(orderedPolicyIds.size == existingIds.size && orderedPolicyIds.toSet() == existingIds) {
            "Policy order must contain every existing policy exactly once"
        }

        database.withTransaction {
            orderedPolicyIds.forEachIndexed { index, id ->
                dao.updatePriority(id, index * 10)
            }
        }
    }

    /** Rewrites member positions after drag-and-drop in a strategy editor. */
    suspend fun reorderStrategyMembers(
        groupId: String,
        orderedMembers: List<Pair<String, String>>,
    ) {
        val dao = database.openStrategyGroupDao()
        val existing = dao.queryMembers(groupId)
            .map { it.memberType to it.memberId }
            .toSet()
        require(orderedMembers.size == existing.size && orderedMembers.toSet() == existing) {
            "Member order must contain every existing member exactly once"
        }

        database.withTransaction {
            orderedMembers.forEachIndexed { index, (type, id) ->
                dao.updateMemberPosition(groupId, type, id, index)
            }
        }
    }
}
