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

val MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
)

val LEGACY_MIGRATION = ::migrationFromLegacy
