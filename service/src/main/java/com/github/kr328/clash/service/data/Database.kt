package com.github.kr328.clash.service.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.service.data.migrations.LEGACY_MIGRATION
import com.github.kr328.clash.service.data.migrations.MIGRATIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.SoftReference
import androidx.room.Database as DB

private suspend fun installProfileNodePresets(dao: ProfileNodeDao) {
    dao.insertGroups(
        listOf(
            ProfileNodeGroup("region-us", "美国", true, sortOrder = 10),
            ProfileNodeGroup("region-jp", "日本", true, sortOrder = 20),
            ProfileNodeGroup("region-sg", "新加坡", true, sortOrder = 30),
            ProfileNodeGroup("region-hk", "香港", true, sortOrder = 40),
            ProfileNodeGroup("region-tw", "台湾", true, sortOrder = 50),
            ProfileNodeGroup("region-kr", "韩国", true, sortOrder = 60),
            ProfileNodeGroup("region-gb", "英国", true, sortOrder = 70),
            ProfileNodeGroup("region-de", "德国", true, sortOrder = 80),
            ProfileNodeGroup("region-other", "其他", true, sortOrder = 90),
        )
    )
}

@DB(
    version = 9,
    entities = [Imported::class, Pending::class, Selection::class, NodeGroup::class, NodeGroupMember::class, StrategyGroup::class, StrategyGroupMember::class, RoutingPolicy::class, PolicyCondition::class, RuleSet::class, ProfileNodeGroup::class, ProfileNodeOverride::class, ProfileNodeSettings::class],
    exportSchema = false,
)

abstract class Database : RoomDatabase() {
    abstract fun openImportedDao(): ImportedDao
    abstract fun openPendingDao(): PendingDao
    abstract fun openSelectionProxyDao(): SelectionDao
    abstract fun openNodeGroupDao(): NodeGroupDao
    abstract fun openStrategyGroupDao(): StrategyGroupDao
    abstract fun openRoutingPolicyDao(): RoutingPolicyDao
    abstract fun openRuleSetDao(): RuleSetDao
    abstract fun openProfileNodeDao(): ProfileNodeDao

    companion object {
        val database: Database
            @Synchronized get() {
                return softDatabase.get() ?: open(Global.application).apply {
                    softDatabase = SoftReference(this)
                }
            }

        private var softDatabase: SoftReference<Database?> = SoftReference(null)

        private fun open(context: Context): Database {
            val database = Room.databaseBuilder(
                context.applicationContext,
                Database::class.java,
                "profiles"
            ).addMigrations(*MIGRATIONS).build()

            Global.launch(Dispatchers.IO) {
                runCatching { DefaultRoutingPreset.installIfEmpty(database) }
                runCatching { installProfileNodePresets(database.openProfileNodeDao()) }
            }
            return database
        }

        init {
            Global.launch(Dispatchers.IO) {
                LEGACY_MIGRATION(Global.application)
            }
        }
    }
}
