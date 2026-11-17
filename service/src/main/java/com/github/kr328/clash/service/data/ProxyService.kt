package com.github.kr328.clash.service.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "proxy_services",
    indices = [Index(value = ["sortOrder"]), Index(value = ["enabled"])],
)
data class ProxyService(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "mode")
    val mode: String = MODE_SELECT,
    @ColumnInfo(name = "sortOrder")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val MODE_SELECT = "SELECT"
        const val MODE_URL_TEST = "URL_TEST"
        const val MODE_FALLBACK = "FALLBACK"
        const val MODE_LOAD_BALANCE = "LOAD_BALANCE"
    }
}

@Entity(
    tableName = "proxy_service_node_groups",
    primaryKeys = ["serviceId", "nodeGroupId"],
    indices = [Index(value = ["nodeGroupId"])],
    foreignKeys = [
        ForeignKey(
            entity = ProxyService::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NodeGroup::class,
            parentColumns = ["id"],
            childColumns = ["nodeGroupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProxyServiceNodeGroup(
    @ColumnInfo(name = "serviceId")
    val serviceId: String,
    @ColumnInfo(name = "nodeGroupId")
    val nodeGroupId: String,
    @ColumnInfo(name = "position")
    val position: Int,
)

@Entity(
    tableName = "proxy_service_target_sets",
    primaryKeys = ["targetSetId"],
    indices = [Index(value = ["serviceId"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetSet::class,
            parentColumns = ["id"],
            childColumns = ["targetSetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProxyService::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProxyServiceTargetSet(
    @ColumnInfo(name = "targetSetId")
    val targetSetId: String,
    @ColumnInfo(name = "serviceId")
    val serviceId: String,
)
