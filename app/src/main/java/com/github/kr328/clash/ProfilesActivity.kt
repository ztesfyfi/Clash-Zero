package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.design.model.ProfileNodeItem
import com.github.kr328.clash.design.model.ProfileNodeGroupItem
import com.github.kr328.clash.design.model.ProfileNodeSettingsItem
import com.github.kr328.clash.service.data.Database
import com.github.kr328.clash.service.data.ProfileNodeGroup
import com.github.kr328.clash.service.data.ProfileNodeOverride
import com.github.kr328.clash.service.data.ProfileNodeSettings
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class ProfilesActivity : BaseActivity<ProfilesDesign>() {
    override suspend fun main() {
        val design = ProfilesDesign(this)

        setContentDesign(design)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ProfileChanged -> {
                            design.fetch()
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProfilesDesign.Request.Create ->
                            startActivity(NewProfileActivity::class.intent)
                        ProfilesDesign.Request.ManageGroups ->
                            design.showNodeGroupManager()
                        is ProfilesDesign.Request.TestProfile -> {
                            if (!it.profile.active) {
                                design.showToast("请先启用这个订阅，再进行测速。", ToastDuration.Long)
                            } else {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val dao = Database.database.openProfileNodeDao()
                                        val profileNodes = Clash.queryProfileProxies(importedDir.resolve(it.profile.uuid.toString()))
                                        if (profileNodes.isEmpty()) throw IllegalStateException("这个订阅没有可测速节点")
                                        val semaphore = Semaphore(5)
                                        val results = coroutineScope {
                                            profileNodes.map { node ->
                                                async(Dispatchers.IO) {
                                                    semaphore.withPermit {
                                                        node to runCatching {
                                                            Clash.testProxy(node.name, "https://www.gstatic.com/generate_204", 5000)
                                                        }
                                                    }
                                                }
                                            }.awaitAll()
                                        }
                                        val oldOverrides = dao.queryOverrides(it.profile.uuid).associateBy { value -> value.nodeKey }
                                        val now = System.currentTimeMillis()
                                        results.forEach { (node, outcome) ->
                                            val old = oldOverrides[node.key]
                                            val tested = outcome.getOrNull()
                                            val delay = tested?.delay?.takeIf { it > 0 }
                                            val error = outcome.exceptionOrNull()?.message
                                                ?: tested?.error
                                                ?: if (delay == null) "测速失败" else null
                                            dao.upsertOverride(
                                                ProfileNodeOverride(
                                                    profileUuid = it.profile.uuid,
                                                    nodeKey = node.key,
                                                    originalName = node.name,
                                                    customName = old?.customName,
                                                    groupId = old?.groupId,
                                                    delayMs = delay,
                                                    testedAt = now,
                                                    testError = error
                                                )
                                            )
                                        }
                                    }
                                    design.fetch()
                                    design.showToast("订阅节点测速完成。", ToastDuration.Long)
                                } catch (error: Exception) {
                                    design.showToast("测速失败：${error.message ?: "未知错误"}", ToastDuration.Long)
                                }
                            }
                        }
                        is ProfilesDesign.Request.TestNode -> {
                            if (!it.profile.active) {
                                design.showToast("请先启用这个订阅，再进行测速。", ToastDuration.Long)
                            } else {
                                try {
                                    val tested = withContext(Dispatchers.IO) {
                                        Clash.testProxy(it.node.originalName, "https://www.gstatic.com/generate_204", 5000)
                                    }
                                    withContext(Dispatchers.IO) {
                                        val dao = Database.database.openProfileNodeDao()
                                        val old = dao.queryOverrides(it.profile.uuid).firstOrNull { value -> value.nodeKey == it.node.key }
                                        val delay = tested.delay.takeIf { it > 0 }
                                        dao.upsertOverride(
                                            ProfileNodeOverride(
                                                profileUuid = it.profile.uuid,
                                                nodeKey = it.node.key,
                                                originalName = it.node.originalName,
                                                customName = old?.customName,
                                                groupId = old?.groupId,
                                                delayMs = delay,
                                                testedAt = System.currentTimeMillis(),
                                                testError = tested.error ?: if (delay == null) "测速失败" else null
                                            )
                                        )
                                    }
                                    design.fetch()
                                    design.showToast(
                                        if (tested.delay > 0 && tested.error == null) "节点测速完成：${tested.delay} ms" else "节点测速失败：${tested.error ?: "连接超时或不可用"}",
                                        ToastDuration.Long
                                    )
                                } catch (error: Exception) {
                                    design.showToast("节点测速失败：${error.message ?: "未知错误"}", ToastDuration.Long)
                                }
                            }
                        }
                        is ProfilesDesign.Request.SetDelayThresholds -> withContext(Dispatchers.IO) {
                            val dao = Database.database.openProfileNodeDao()
                            val old = dao.querySettings() ?: ProfileNodeSettings(presetsInitialized = true)
                            dao.upsertSettings(old.copy(goodThresholdMs = it.good, slowThresholdMs = it.slow))
                            design.fetch()
                            design.showNodeGroupManager()
                        }
                        is ProfilesDesign.Request.RenameNode -> withContext(Dispatchers.IO) {
                            val dao = Database.database.openProfileNodeDao()
                            val old = dao.queryOverrides(it.profile.uuid).firstOrNull { value -> value.nodeKey == it.node.key }
                            dao.upsertOverride(
                                ProfileNodeOverride(
                                    it.profile.uuid, it.node.key, it.node.originalName,
                                    it.name.takeIf { name -> name.isNotBlank() && name != it.node.originalName },
                                    old?.groupId,
                                    old?.delayMs,
                                    old?.testedAt,
                                    old?.testError
                                )
                            )
                            design.fetch()
                        }
                        is ProfilesDesign.Request.SetNodeGroup -> withContext(Dispatchers.IO) {
                            val dao = Database.database.openProfileNodeDao()
                            val old = dao.queryOverrides(it.profile.uuid).firstOrNull { value -> value.nodeKey == it.node.key }
                            dao.upsertOverride(
                                ProfileNodeOverride(
                                    it.profile.uuid, it.node.key, it.node.originalName,
                                    old?.customName, it.groupId,
                                    old?.delayMs,
                                    old?.testedAt,
                                    old?.testError
                                )
                            )
                            design.fetch()
                        }
                        is ProfilesDesign.Request.CreateNodeGroup -> withContext(Dispatchers.IO) {
                            val name = it.name.trim()
                            if (name.isNotEmpty()) {
                                val dao = Database.database.openProfileNodeDao()
                                val groups = dao.queryGroups()
                                if (groups.none { group -> group.name.equals(name, true) }) {
                                    dao.upsertGroup(
                                        ProfileNodeGroup(
                                            id = UUID.randomUUID().toString(),
                                            name = name,
                                            isPreset = false,
                                            enabled = true,
                                            sortOrder = (groups.maxOfOrNull { group -> group.sortOrder } ?: 0) + 10
                                        )
                                    )
                                }
                            }
                            design.fetch()
                            design.showNodeGroupManager()
                        }
                        is ProfilesDesign.Request.EnableNodeGroup -> withContext(Dispatchers.IO) {
                            Database.database.openProfileNodeDao().setGroupEnabled(it.id, it.enabled)
                            design.fetch()
                            design.showNodeGroupManager()
                        }
                        is ProfilesDesign.Request.DeleteNodeGroup -> withContext(Dispatchers.IO) {
                            val dao = Database.database.openProfileNodeDao()
                            dao.clearGroupAssignments(it.id)
                            dao.deleteGroup(it.id)
                            design.fetch()
                            design.showNodeGroupManager()
                        }
                        ProfilesDesign.Request.Refresh -> design.fetch()
                        ProfilesDesign.Request.UpdateAll ->
                            withProfile {
                                try {
                                    queryAll().forEach { p ->
                                        if (p.imported && p.type != Profile.Type.File)
                                            update(p.uuid)
                                    }
                                }
                                finally {
                                    withContext(Dispatchers.Main) {
                                        design.finishUpdateAll();
                                    }
                                }
                            }
                        is ProfilesDesign.Request.Update ->
                            withProfile { update(it.profile.uuid) }
                        is ProfilesDesign.Request.Delete -> {
                            withProfile { delete(it.profile.uuid) }
                            withContext(Dispatchers.IO) {
                                Database.database.openProfileNodeDao().deleteProfileOverrides(it.profile.uuid)
                            }
                        }
                        is ProfilesDesign.Request.Edit ->
                            startActivity(PropertiesActivity::class.intent.setUUID(it.profile.uuid))
                        is ProfilesDesign.Request.Active -> {
                            withProfile {
                                if (it.profile.imported)
                                    setActive(it.profile)
                                else
                                    design.requestSave(it.profile)
                            }
                        }
                        is ProfilesDesign.Request.Duplicate -> {
                            val uuid = withProfile { clone(it.profile.uuid) }

                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                        is ProfilesDesign.Request.ShareQrCode ->
                            design.showProfileQrCode(it.profile)
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private suspend fun ProfilesDesign.fetch() {
        val profiles = withProfile { queryAll() }
        val dao = Database.database.openProfileNodeDao()
        val settings = withContext(Dispatchers.IO) {
            val existing = dao.querySettings()
            if (existing == null || !existing.presetsInitialized) {
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
                val initialized = (existing ?: ProfileNodeSettings()).copy(presetsInitialized = true)
                dao.upsertSettings(initialized)
                initialized
            } else existing
        }
        val groups = withContext(Dispatchers.IO) { dao.queryGroups() }
        val groupById = groups.associateBy { it.id }
        val nodeMap = withContext(Dispatchers.IO) {
            profiles.filter { it.imported }.associate { profile ->
                val rawNodes = runCatching {
                    Clash.queryProfileProxies(importedDir.resolve(profile.uuid.toString()))
                }.getOrDefault(emptyList())
                val overrides = dao.queryOverrides(profile.uuid).associateBy { it.nodeKey }
                profile.uuid to rawNodes.map { node ->
                    val override = overrides[node.key]
                    val group = override?.groupId?.let(groupById::get)
                    val delayMs = override?.delayMs
                    val testedAt = override?.testedAt
                    val goodThreshold = settings.goodThresholdMs
                    val slowThreshold = settings.slowThresholdMs
                    ProfileNodeItem(
                        key = node.key,
                        originalName = node.name,
                        displayName = override?.customName?.takeIf { it.isNotBlank() } ?: node.name,
                        type = node.type,
                        groupId = group?.id,
                        groupName = group?.name,
                        delayText = when {
                            delayMs != null -> "$delayMs ms"
                            testedAt != null -> override?.testError ?: "测速失败"
                            else -> "未测试"
                        },
                        delayState = when {
                            delayMs == null && testedAt == null -> "UNTESTED"
                            delayMs == null && testedAt != null -> "FAILED"
                            delayMs == null -> "UNTESTED"
                            goodThreshold != null && delayMs <= goodThreshold -> "GOOD"
                            slowThreshold != null && delayMs <= slowThreshold -> "SLOW"
                            goodThreshold != null && slowThreshold != null -> "ERROR"
                            else -> "TESTED"
                        },
                    )
                }
            }
        }
        patchProfiles(
            profiles,
            nodeMap,
            groups.map { ProfileNodeGroupItem(it.id, it.name, it.isPreset, it.enabled, it.sortOrder) },
            ProfileNodeSettingsItem(settings.goodThresholdMs, settings.slowThresholdMs)
        )
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_failed, name, reason),
                ToastDuration.Long
            ){
                setAction(R.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }
}
