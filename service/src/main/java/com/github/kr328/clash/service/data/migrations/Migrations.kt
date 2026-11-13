package com.github.kr328.clash.service.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE imported ADD COLUMN ageSecretKey TEXT")
        database.execSQL("ALTER TABLE pending ADD COLUMN ageSecretKey TEXT")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS node_groups (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                mode TEXT NOT NULL,
                nameTag TEXT,
                regionCode TEXT,
                enabled INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_node_groups_mode ON node_groups(mode)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_node_groups_nameTag ON node_groups(nameTag)")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS node_group_members (
                groupId TEXT NOT NULL,
                profileUuid TEXT NOT NULL,
                proxyKey TEXT NOT NULL,
                proxyName TEXT NOT NULL,
                PRIMARY KEY(groupId, profileUuid, proxyKey),
                FOREIGN KEY(groupId) REFERENCES node_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(profileUuid) REFERENCES imported(uuid) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_node_group_members_profileUuid ON node_group_members(profileUuid)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE node_groups ADD COLUMN iconType TEXT")
        database.execSQL("ALTER TABLE node_groups ADD COLUMN iconValue TEXT")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE node_groups ADD COLUMN iconMode TEXT NOT NULL DEFAULT 'AUTO'")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS strategy_groups (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                mode TEXT NOT NULL,
                iconMode TEXT NOT NULL,
                iconType TEXT,
                iconValue TEXT,
                sortOrder INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_strategy_groups_sortOrder ON strategy_groups(sortOrder)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_strategy_groups_enabled ON strategy_groups(enabled)")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS strategy_group_members (
                groupId TEXT NOT NULL,
                memberType TEXT NOT NULL,
                memberId TEXT NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY(groupId, memberType, memberId),
                FOREIGN KEY(groupId) REFERENCES strategy_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_strategy_group_members_groupId_position ON strategy_group_members(groupId, position)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_strategy_group_members_memberType_memberId ON strategy_group_members(memberType, memberId)")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS routing_policies (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                targetType TEXT NOT NULL,
                targetId TEXT,
                matchMode TEXT NOT NULL,
                iconMode TEXT NOT NULL,
                iconType TEXT,
                iconValue TEXT,
                priority INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_routing_policies_priority ON routing_policies(priority)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_routing_policies_enabled ON routing_policies(enabled)")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS policy_conditions (
                id TEXT NOT NULL,
                policyId TEXT NOT NULL,
                conditionGroup TEXT NOT NULL,
                groupMode TEXT NOT NULL,
                matcherType TEXT NOT NULL,
                matcherValue TEXT NOT NULL,
                negated INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(policyId) REFERENCES routing_policies(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_policy_conditions_policyId_conditionGroup ON policy_conditions(policyId, conditionGroup)")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rule_sets (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                sourceType TEXT NOT NULL,
                sourceValue TEXT NOT NULL,
                format TEXT NOT NULL,
                updateInterval INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_rule_sets_kind ON rule_sets(kind)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_rule_sets_sourceType ON rule_sets(sourceType)")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Remove only legacy Android app-match conditions. Preserve other conditions
        // belonging to the same policy and disable policies left without conditions.
        database.execSQL(
            """
            CREATE TEMP TABLE legacy_app_policy_ids AS
            SELECT DISTINCT policyId FROM policy_conditions
            WHERE matcherType = 'PROCESS_PACKAGE'
            """.trimIndent()
        )
        database.execSQL("DELETE FROM policy_conditions WHERE matcherType = 'PROCESS_PACKAGE'")
        database.execSQL(
            """
            UPDATE routing_policies
            SET enabled = 0, updatedAt = strftime('%s','now') * 1000
            WHERE id IN (SELECT policyId FROM legacy_app_policy_ids)
              AND NOT EXISTS (
                  SELECT 1 FROM policy_conditions
                  WHERE policy_conditions.policyId = routing_policies.id
              )
            """.trimIndent()
        )
        database.execSQL("DROP TABLE legacy_app_policy_ids")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS profile_node_groups (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                isPreset INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_profile_node_groups_sortOrder ON profile_node_groups(sortOrder)")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS profile_node_overrides (
                profileUuid TEXT NOT NULL,
                nodeKey TEXT NOT NULL,
                originalName TEXT NOT NULL,
                customName TEXT,
                groupId TEXT,
                PRIMARY KEY(profileUuid, nodeKey)
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_profile_node_overrides_groupId ON profile_node_overrides(groupId)")
    }
}


private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE profile_node_overrides ADD COLUMN delayMs INTEGER")
        database.execSQL("ALTER TABLE profile_node_overrides ADD COLUMN testedAt INTEGER")
        database.execSQL("ALTER TABLE profile_node_overrides ADD COLUMN testError TEXT")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS profile_node_settings (
                id INTEGER NOT NULL,
                goodThresholdMs INTEGER,
                slowThresholdMs INTEGER,
                presetsInitialized INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(id)
            )
        """.trimIndent())
    }
}

val MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
)

val LEGACY_MIGRATION = ::migrationFromLegacy


