package com.github.kr328.clash

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.service.data.Database
import com.github.kr328.clash.service.data.NodeGroup
import com.github.kr328.clash.service.data.PolicyCondition
import com.github.kr328.clash.service.data.RoutingPolicy
import com.github.kr328.clash.service.data.StrategyConfigurationRepository
import com.github.kr328.clash.service.data.StrategyGroup
import com.github.kr328.clash.service.data.StrategyGroupMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Visual CRUD surface for the persisted strategy configuration models. */
class StrategyManagerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SECTION = "strategy_manager_section"
        const val SECTION_NODE_GROUPS = "node_groups"
        const val SECTION_STRATEGY_GROUPS = "strategy_groups"
        const val SECTION_POLICIES = "policies"
    }

    private val scope: CoroutineScope = MainScope()
    private val database get() = Database.database
    private lateinit var content: LinearLayout
    private lateinit var section: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        section = intent.getStringExtra(EXTRA_SECTION) ?: SECTION_NODE_GROUPS
        val title = when (section) {
            SECTION_NODE_GROUPS -> "节点组"
            SECTION_STRATEGY_GROUPS -> "策略组"
            else -> "策略"
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(16))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Button(this).apply {
            text = "‹ 返回"
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            text = title
            textSize = 22f
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(Button(this).apply {
            text = "＋ 新建"
            setOnClickListener { createItem() }
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = when (section) {
                SECTION_NODE_GROUPS -> "静态组、名称动态组和名称 + 地区动态组。地区组的落地 IP 识别与静态节点选择器仍需接入。"
                SECTION_STRATEGY_GROUPS -> "策略组可组合节点组或其他策略组；保存时检查引用有效性和循环引用。"
                else -> "按域名、IP、GeoIP、GeoSite 或应用包名匹配，目标可选策略组、直连或拒绝。"
            }
            textSize = 13f
            setPadding(dp(4), dp(4), dp(4), dp(14))
        })
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun createItem() {
        when (section) {
            SECTION_NODE_GROUPS -> createNodeGroup()
            SECTION_STRATEGY_GROUPS -> createStrategyGroup()
            else -> createPolicy()
        }
    }

    private fun refresh() {
        scope.launch {
            try {
                content.removeAllViews()
                when (section) {
                    SECTION_NODE_GROUPS -> {
                        val groups = database.openNodeGroupDao().queryAll()
                        if (groups.isEmpty()) emptyHint("还没有节点组，点击右上角新建。")
                        groups.forEach { group ->
                            val detail = when (group.mode) {
                                NodeGroup.MODE_STATIC -> "静态组"
                                NodeGroup.MODE_NAME -> "名称动态组 · ${group.nameTag.orEmpty()}"
                                else -> "名称 + 地区动态组 · ${group.nameTag.orEmpty()} · ${group.regionCode.orEmpty()}"
                            }
                            addRow(group.name, detail, group.enabled) { editNodeGroup(group) }
                        }
                    }
                    SECTION_STRATEGY_GROUPS -> {
                        val groups = database.openStrategyGroupDao().queryAll()
                        if (groups.isEmpty()) emptyHint("还没有策略组。")
                        groups.forEach { group ->
                            val members = database.openStrategyGroupDao().queryMembers(group.id)
                            addRow(group.name, "${strategyModeName(group.mode)} · ${members.size} 个成员", group.enabled) {
                                manageStrategyGroup(group)
                            }
                        }
                    }
                    else -> {
                        val policies = database.openRoutingPolicyDao().queryAll()
                        if (policies.isEmpty()) emptyHint("还没有策略。")
                        policies.forEach { policy ->
                            val conditions = database.openRoutingPolicyDao().queryConditions(policy.id)
                            val target = when (policy.targetType) {
                                RoutingPolicy.TARGET_DIRECT -> "DIRECT"
                                RoutingPolicy.TARGET_REJECT -> "REJECT"
                                else -> database.openStrategyGroupDao().queryById(policy.targetId.orEmpty())?.name ?: "目标组缺失"
                            }
                            addRow(policy.name, "目标：$target · 条件：${conditions.size} · 优先级：${policy.priority}", policy.enabled) {
                                editPolicy(policy)
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                toast("读取失败：${error.message}")
            }
        }
    }

    private fun createNodeGroup() {
        val name = edit("节点组名称")
        val tag = edit("关联名称（名称动态组必填）")
        val region = edit("地区代码（如 US、JP；地区动态组填写）")
        val mode = spinner(listOf("静态组", "名称动态组", "名称 + 地区动态组"))
        formDialog("新建节点组", listOf("名称", "关联名称", "地区代码"), listOf(name, tag, region), mode) {
            val groupName = name.text.toString().trim()
            if (groupName.isBlank()) return@formDialog toast("请输入节点组名称")
            val selectedMode = when (mode.selectedItemPosition) {
                1 -> NodeGroup.MODE_NAME
                2 -> NodeGroup.MODE_NAME_REGION
                else -> NodeGroup.MODE_STATIC
            }
            val nameTag = tag.text.toString().trim().ifBlank { null }
            val regionCode = region.text.toString().trim().uppercase().ifBlank { null }
            if (selectedMode != NodeGroup.MODE_STATIC && nameTag == null) return@formDialog toast("动态组必须填写关联名称")
            if (selectedMode == NodeGroup.MODE_NAME_REGION && regionCode == null) return@formDialog toast("地区动态组必须填写地区代码")
            scope.launch {
                runCatching {
                    database.openNodeGroupDao().upsert(NodeGroup(
                        name = groupName, mode = selectedMode, nameTag = nameTag, regionCode = regionCode
                    ))
                }.onSuccess { refresh() }.onFailure { toast("保存失败：${it.message}") }
            }
        }
    }

    private fun editNodeGroup(group: NodeGroup) {
        AlertDialog.Builder(this).setTitle(group.name)
            .setItems(arrayOf(if (group.enabled) "停用" else "启用", "删除")) { _, which ->
                scope.launch {
                    if (which == 0) database.openNodeGroupDao().upsert(group.copy(enabled = !group.enabled))
                    else database.openNodeGroupDao().delete(group.id)
                    refresh()
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun createStrategyGroup() {
        val name = edit("策略组名称")
        val mode = spinner(listOf("手动选择", "URL 自动测速", "故障转移", "负载均衡"))
        formDialog("新建策略组", listOf("名称"), listOf(name), mode) {
            val groupName = name.text.toString().trim()
            if (groupName.isBlank()) return@formDialog toast("请输入策略组名称")
            val groupMode = when (mode.selectedItemPosition) {
                1 -> StrategyGroup.MODE_URL_TEST
                2 -> StrategyGroup.MODE_FALLBACK
                3 -> StrategyGroup.MODE_LOAD_BALANCE
                else -> StrategyGroup.MODE_SELECT
            }
            scope.launch {
                runCatching {
                    database.openStrategyGroupDao().upsert(StrategyGroup(
                        name = groupName, mode = groupMode,
                        sortOrder = database.openStrategyGroupDao().queryAll().size * 10
                    ))
                }.onSuccess { refresh() }.onFailure { toast("保存失败：${it.message}") }
            }
        }
    }

    private fun manageStrategyGroup(group: StrategyGroup) {
        scope.launch {
            val nodeGroups = database.openNodeGroupDao().queryAll()
            val strategyGroups = database.openStrategyGroupDao().queryAll().filterNot { it.id == group.id }
            val options = nodeGroups.map { "添加节点组 · ${it.name}" } +
                strategyGroups.map { "添加策略组 · ${it.name}" } +
                listOf(if (group.enabled) "停用" else "启用", "删除")
            AlertDialog.Builder(this@StrategyManagerActivity).setTitle("${group.name} · 成员管理")
                .setItems(options.toTypedArray()) { _, index ->
                    when {
                        index < nodeGroups.size -> addStrategyMember(group, StrategyGroupMember.TYPE_NODE_GROUP, nodeGroups[index].id)
                        index < nodeGroups.size + strategyGroups.size -> {
                            val child = strategyGroups[index - nodeGroups.size]
                            addStrategyMember(group, StrategyGroupMember.TYPE_STRATEGY_GROUP, child.id)
                        }
                        index == options.size - 2 -> scope.launch {
                            database.openStrategyGroupDao().upsert(group.copy(enabled = !group.enabled))
                            refresh()
                        }
                        else -> deleteStrategyGroup(group)
                    }
                }.setNegativeButton("关闭", null).show()
        }
    }

    private fun addStrategyMember(group: StrategyGroup, type: String, id: String) {
        scope.launch {
            try {
                val dao = database.openStrategyGroupDao()
                val members = dao.queryMembers(group.id)
                if (members.any { it.memberType == type && it.memberId == id }) {
                    toast("该成员已存在")
                    return@launch
                }
                StrategyConfigurationRepository(database).saveStrategyGroup(
                    group, members + StrategyGroupMember(group.id, type, id, members.size)
                )
                toast("成员已添加")
                refresh()
            } catch (error: Exception) {
                toast("未能添加：${error.message}")
            }
        }
    }

    private fun deleteStrategyGroup(group: StrategyGroup) {
        scope.launch {
            val parents = database.openStrategyGroupDao().queryParents(
                StrategyGroupMember.TYPE_STRATEGY_GROUP, group.id
            )
            if (parents.isNotEmpty()) {
                toast("该策略组仍被其他策略组引用，请先移除引用")
                return@launch
            }
            database.openStrategyGroupDao().delete(group.id)
            refresh()
        }
    }

    private fun createPolicy() {
        scope.launch {
            val groups = database.openStrategyGroupDao().queryAll()
            val nextPriority = database.openRoutingPolicyDao().queryAll().size * 10
            val name = edit("策略名称")
            val matcherType = spinner(listOf(
                "域名 DOMAIN", "域名后缀 DOMAIN_SUFFIX", "域名关键词 DOMAIN_KEYWORD",
                "IP IP", "IP 网段 IP_CIDR", "GeoIP GEOIP", "GeoSite GEOSITE", "应用包名 PROCESS_PACKAGE"
            ))
            val matcherValue = edit("匹配值（如 youtube、cn、example.com）")
            val target = spinner(listOf("直连 DIRECT", "拒绝 REJECT") + groups.map { "策略组 · ${it.name}" })
            val matchMode = spinner(listOf("满足任意条件 ANY", "满足全部条件 ALL"))
            val form = LinearLayout(this@StrategyManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), 0)
                addView(name); addView(matcherType); addView(matcherValue); addView(target); addView(matchMode)
            }
            AlertDialog.Builder(this@StrategyManagerActivity).setTitle("新建策略").setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存") { _, _ ->
                    val policyName = name.text.toString().trim()
                    val value = matcherValue.text.toString().trim()
                    if (policyName.isBlank() || value.isBlank()) {
                        toast("策略名称和匹配值不能为空")
                        return@setPositiveButton
                    }
                    val targetIndex = target.selectedItemPosition
                    val targetType = when (targetIndex) {
                        0 -> RoutingPolicy.TARGET_DIRECT
                        1 -> RoutingPolicy.TARGET_REJECT
                        else -> RoutingPolicy.TARGET_STRATEGY_GROUP
                    }
                    val targetId = if (targetIndex >= 2) groups[targetIndex - 2].id else null
                    val matcher = matcherType.selectedItem.toString().substringAfterLast(' ')
                    val policy = RoutingPolicy(
                        name = policyName,
                        targetType = targetType,
                        targetId = targetId,
                        matchMode = if (matchMode.selectedItemPosition == 1) RoutingPolicy.MATCH_ALL else RoutingPolicy.MATCH_ANY,
                        priority = nextPriority
                    )
                    val condition = PolicyCondition(
                        policyId = policy.id,
                        matcherType = matcher,
                        matcherValue = value,
                        groupMode = PolicyCondition.GROUP_ANY
                    )
                    scope.launch {
                        try {
                            StrategyConfigurationRepository(database).savePolicy(policy, listOf(condition))
                            refresh()
                        } catch (error: Exception) {
                            toast("保存失败：${error.message}")
                        }
                    }
                }.show()
        }
    }

    private fun editPolicy(policy: RoutingPolicy) {
        AlertDialog.Builder(this).setTitle(policy.name)
            .setItems(arrayOf(if (policy.enabled) "停用" else "启用", "删除")) { _, which ->
                scope.launch {
                    if (which == 0) database.openRoutingPolicyDao().setEnabled(policy.id, !policy.enabled)
                    else database.openRoutingPolicyDao().delete(policy.id)
                    refresh()
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun formDialog(
        dialogTitle: String,
        labels: List<String>,
        fields: List<EditText>,
        selector: Spinner,
        save: () -> Unit
    ) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            labels.zip(fields).forEach { (label, field) ->
                addView(TextView(this@StrategyManagerActivity).apply { text = label; textSize = 12f })
                addView(field)
            }
            addView(selector)
        }
        AlertDialog.Builder(this).setTitle(dialogTitle).setView(form)
            .setNegativeButton("取消", null).setPositiveButton("保存") { _, _ -> save() }.show()
    }

    private fun addRow(name: String, detail: String, enabled: Boolean, click: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0x12000000)
            }
        }
        row.addView(TextView(this).apply {
            text = name + if (enabled) "" else "（已停用）"
            textSize = 17f
        })
        row.addView(TextView(this).apply {
            text = detail
            textSize = 12f
            alpha = 0.72f
        })
        row.setOnClickListener { click() }
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(8)
        content.addView(row, params)
    }

    private fun emptyHint(message: String) {
        content.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setPadding(dp(12), dp(24), dp(12), dp(24))
        })
    }

    private fun edit(hint: String) = EditText(this).apply { this.hint = hint; isSingleLine = true }

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@StrategyManagerActivity, android.R.layout.simple_spinner_dropdown_item, items)
    }

    private fun strategyModeName(mode: String) = when (mode) {
        StrategyGroup.MODE_URL_TEST -> "URL 自动测速"
        StrategyGroup.MODE_FALLBACK -> "故障转移"
        StrategyGroup.MODE_LOAD_BALANCE -> "负载均衡"
        else -> "手动选择"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
