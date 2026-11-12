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

@DB(
    version = 6,
    entities = [Imported::class, Pending::class, Selection::class, NodeGroup::class, NodeGroupMember::class, StrategyGroup::class, StrategyGroupMember::class, RoutingPolicy::class, PolicyCondition::class, RuleSet::class],
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
