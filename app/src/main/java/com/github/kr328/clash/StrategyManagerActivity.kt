package com.github.kr328.clash

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kr328.clash.service.data.Database
import com.github.kr328.clash.service.data.NodeGroup
import com.github.kr328.clash.service.data.PolicyCondition
import com.github.kr328.clash.service.data.RoutingPolicy
import com.github.kr328.clash.service.data.StrategyConfigurationRepository
import com.github.kr328.clash.service.data.StrategyGroup
import com.github.kr328.clash.service.data.StrategyGroupMember
import com.github.kr328.clash.service.util.sendOverrideChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Visual editor for outbound node groups, proxy strategies and target routing. */
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
            SECTION_NODE_GROUPS -> "出站节点"
            SECTION_STRATEGY_GROUPS -> "代理策略"
            else -> "目标分流"
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(20), bars.top + dp(12), dp(20), bars.bottom + dp(16))
            insets
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
                SECTION_NODE_GROUPS -> "统一管理静态与动态出站节点组。"
                SECTION_STRATEGY_GROUPS -> "管理代理策略类型、成员组合及出口选择方式。"
                else -> "点击策略项可修改匹配条件和目标策略组。"
            }
            textSize = 13f
            setPadding(dp(4), dp(4), dp(4), dp(14))
        })
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        refresh()
    }

    private fun createItem() {
        when (section) {
            SECTION_NODE_GROUPS -> showNodeGroupEditor(null)
            SECTION_STRATEGY_GROUPS -> showStrategyGroupEditor(null)
            else -> createPolicy()
        }
    }

    private fun refresh(apply: Boolean = false) {
        if (apply) sendOverrideChanged()
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
                            addRow(group.name, detail, group.enabled) { showNodeGroupActions(group) }
                        }
                    }
                    SECTION_STRATEGY_GROUPS -> {
                        val groups = database.openStrategyGroupDao().queryAll()
                        if (groups.isEmpty()) emptyHint("还没有策略组。")
                        groups.forEach { group ->
                            val members = database.openStrategyGroupDao().queryMembers(group.id)
                            addRow(group.name, "${strategyModeName(group.mode)} · ${members.size} 个成员", group.enabled) {
                                showStrategyGroupEditor(group)
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
                            val matcherSummary = conditions.joinToString("、") { "${it.matcherType}: ${it.matcherValue}" }
                            addRow(policy.name, "出口：$target · ${matcherSummary.ifBlank { "未设置条件" }} · 优先级：${policy.priority}", policy.enabled) {
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

    private fun showNodeGroupActions(group: NodeGroup) {
        AlertDialog.Builder(this).setTitle(group.name)
            .setItems(arrayOf("编辑", if (group.enabled) "停用" else "启用", "删除")) { _, which ->
                when (which) {
                    0 -> showNodeGroupEditor(group)
                    1 -> scope.launch {
                        database.openNodeGroupDao().upsert(group.copy(enabled = !group.enabled, updatedAt = System.currentTimeMillis()))
                        refresh(apply = true)
                    }
                    2 -> scope.launch {
                        val usedBy = database.openStrategyGroupDao().queryParents(
                            StrategyGroupMember.TYPE_NODE_GROUP, group.id
                        )
                        if (usedBy.isNotEmpty()) {
                            toast("该节点组仍被策略组引用，请先从策略组成员中移除")
                        } else {
                            database.openNodeGroupDao().delete(group.id)
                            refresh(apply = true)
                        }
                    }
                }
            }.setNegativeButton("取消", null).show()
    }

    /** Reuses the original ID when editing, so existing references are never broken. */
    private fun showNodeGroupEditor(existing: NodeGroup?) {
        val name = edit("节点组名称").apply { setText(existing?.name.orEmpty()) }
        val tag = edit("关联名称（动态组必填）").apply { setText(existing?.nameTag.orEmpty()) }
        val region = edit("地区代码（如 US、JP）").apply { setText(existing?.regionCode.orEmpty()) }
        val mode = spinner(listOf("静态组", "名称动态组", "名称 + 地区动态组"))
        mode.setSelection(when (existing?.mode) {
            NodeGroup.MODE_NAME -> 1
            NodeGroup.MODE_NAME_REGION -> 2
            else -> 0
        })
        val form = formView(listOf("名称", "关联名称", "地区代码"), listOf(name, tag, region), mode)
        AlertDialog.Builder(this).setTitle(if (existing == null) "新建节点组" else "编辑节点组")
            .setView(form)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val groupName = name.text.toString().trim()
                if (groupName.isBlank()) return@setPositiveButton toast("请输入节点组名称")
                val selectedMode = when (mode.selectedItemPosition) {
                    1 -> NodeGroup.MODE_NAME
                    2 -> NodeGroup.MODE_NAME_REGION
                    else -> NodeGroup.MODE_STATIC
                }
                val nameTag = tag.text.toString().trim().ifBlank { null }
                val regionCode = region.text.toString().trim().uppercase().ifBlank { null }
                if (selectedMode != NodeGroup.MODE_STATIC && nameTag == null) {
                    return@setPositiveButton toast("动态组必须填写关联名称")
                }
                if (selectedMode == NodeGroup.MODE_NAME_REGION && regionCode == null) {
                    return@setPositiveButton toast("地区动态组必须填写地区代码")
                }
                scope.launch {
                    runCatching {
                        val group = existing?.copy(
                            name = groupName,
                            mode = selectedMode,
                            nameTag = nameTag,
                            regionCode = regionCode,
                            updatedAt = System.currentTimeMillis()
                        ) ?: NodeGroup(name = groupName, mode = selectedMode, nameTag = nameTag, regionCode = regionCode)
                        database.openNodeGroupDao().upsert(group)
                    }.onSuccess { refresh(apply = true) }.onFailure { toast("保存失败：${it.message}") }
                }
            }.show()
    }

    private fun showStrategyGroupEditor(existing: StrategyGroup?) {
        val name = edit("代理分流规则名称").apply { setText(existing?.name.orEmpty()) }
        val mode = spinner(listOf("手动选择", "自动择优", "故障转移", "负载均衡"))
        mode.setSelection(when (existing?.mode) {
            StrategyGroup.MODE_URL_TEST -> 1
            StrategyGroup.MODE_FALLBACK -> 2
            StrategyGroup.MODE_LOAD_BALANCE -> 3
            else -> 0
        })
        val memberButton = Button(this).apply {
            text = "编辑此策略的成员"
            isEnabled = existing != null
            setOnClickListener { existing?.let(::manageStrategyGroupMembers) }
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(label("代理策略名称")); addView(name)
            addView(label("策略类型")); addView(mode)
            addView(TextView(this@StrategyManagerActivity).apply {
                text = "策略成员可引用出站节点组或其他代理策略；不在这里绑定具体节点或 Android 应用。"
                textSize = 12f
                alpha = 0.75f
                setPadding(0, dp(8), 0, dp(8))
            })
            addView(memberButton)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "新建代理策略" else "编辑：${existing.name}")
            .setView(form)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val groupName = name.text.toString().trim()
                if (groupName.isBlank()) {
                    toast("请输入代理策略名称")
                    return@setOnClickListener
                }
                val groupMode = when (mode.selectedItemPosition) {
                    1 -> StrategyGroup.MODE_URL_TEST
                    2 -> StrategyGroup.MODE_FALLBACK
                    3 -> StrategyGroup.MODE_LOAD_BALANCE
                    else -> StrategyGroup.MODE_SELECT
                }
                val saved = existing?.copy(name = groupName, mode = groupMode, updatedAt = System.currentTimeMillis())
                    ?: StrategyGroup(name = groupName, mode = groupMode)
                dialog.dismiss()
                scope.launch {
                    runCatching { database.openStrategyGroupDao().upsert(saved) }
                        .onSuccess { toast("代理策略已保存"); refresh(apply = true) }
                        .onFailure { toast("保存失败：${it.message}") }
                }
            }
        }
        dialog.show()
    }

    private fun manageStrategyGroupMembers(group: StrategyGroup) {
        scope.launch {
            val dao = database.openStrategyGroupDao()
            val members = dao.queryMembers(group.id)
            val nodeGroups = database.openNodeGroupDao().queryAll()
            val strategyGroups = dao.queryAll().filterNot { it.id == group.id }
            val memberLabels = members.map { member ->
                val name = when (member.memberType) {
                    StrategyGroupMember.TYPE_NODE_GROUP ->
                        nodeGroups.firstOrNull { it.id == member.memberId }?.name ?: "节点组已删除"
                    else -> strategyGroups.firstOrNull { it.id == member.memberId }?.name ?: "策略组已删除"
                }
                "${if (member.memberType == StrategyGroupMember.TYPE_NODE_GROUP) "节点组" else "策略组"} · $name"
            }
            val options = memberLabels + nodeGroups.map { "添加节点组 · ${it.name}" } +
                strategyGroups.map { "添加策略组 · ${it.name}" }
            AlertDialog.Builder(this@StrategyManagerActivity).setTitle("${group.name} · 成员")
                .setItems(options.toTypedArray()) { _, index ->
                    when {
                        index < members.size -> {
                            val member = members[index]
                            scope.launch {
                                dao.removeMember(group.id, member.memberType, member.memberId)
                                refresh(apply = true)
                            }
                        }
                        index < members.size + nodeGroups.size -> {
                            val child = nodeGroups[index - members.size]
                            addStrategyMember(group, StrategyGroupMember.TYPE_NODE_GROUP, child.id)
                        }
                        else -> {
                            val child = strategyGroups[index - members.size - nodeGroups.size]
                            addStrategyMember(group, StrategyGroupMember.TYPE_STRATEGY_GROUP, child.id)
                        }
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
                refresh(apply = true)
            } catch (error: Exception) {
                toast("未能添加：${error.message}")
            }
        }
    }

    private fun createPolicy() {
        scope.launch {
            val groups = database.openStrategyGroupDao().queryAll()
            val name = edit("策略名称")
            val matcherType = spinner(listOf(
                "域名 DOMAIN", "域名后缀 DOMAIN_SUFFIX", "域名关键词 DOMAIN_KEYWORD",
                "IP IP", "IP 网段 IP_CIDR", "GeoIP GEOIP", "GeoSite GEOSITE"
            ))
            val matcherValue = edit("匹配值（如 youtube、cn、example.com 或 IP 网段）")
            val target = spinner(targetOptions(groups))
            val priority = edit("优先级（数字越小越先匹配）").apply { setText((database.openRoutingPolicyDao().queryAll().size * 10).toString()) }
            val form = LinearLayout(this@StrategyManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), 0)
                addView(label("策略名称")); addView(name)
                addView(label("匹配类型")); addView(matcherType)
                addView(label("匹配值")); addView(matcherValue)
                addView(label("流量目标")); addView(target)
                addView(label("优先级")); addView(priority)
            }
            AlertDialog.Builder(this@StrategyManagerActivity).setTitle("新建策略").setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存") { _, _ ->
                    val policyName = name.text.toString().trim()
                    val value = matcherValue.text.toString().trim()
                    if (policyName.isBlank() || value.isBlank()) return@setPositiveButton toast("策略名称和匹配值不能为空")
                    val targetIndex = target.selectedItemPosition
                    val selectedTarget = policyTarget(targetIndex, groups)
                    val matcher = matcherType.selectedItem.toString().substringAfterLast(' ')
                    val policy = RoutingPolicy(
                        name = policyName,
                        targetType = selectedTarget.first,
                        targetId = selectedTarget.second,
                        priority = priority.text.toString().toIntOrNull() ?: 0
                    )
                    val condition = PolicyCondition(
                        policyId = policy.id,
                        matcherType = matcher,
                        matcherValue = value
                    )
                    scope.launch {
                        try {
                            StrategyConfigurationRepository(database).savePolicy(policy, listOf(condition))
                            refresh(apply = true)
                        } catch (error: Exception) {
                            toast("保存失败：${error.message}")
                        }
                    }
                }.show()
        }
    }

    private fun editPolicy(policy: RoutingPolicy) {
        scope.launch {
            val groups = database.openStrategyGroupDao().queryAll()
            val conditions = database.openRoutingPolicyDao().queryConditions(policy.id)
            val name = edit("策略名称").apply { setText(policy.name) }
            val priority = edit("优先级（数字越小越先匹配）").apply { setText(policy.priority.toString()) }
            val target = spinner(targetOptions(groups))
            val targetIndex = when (policy.targetType) {
                RoutingPolicy.TARGET_DIRECT -> 0
                RoutingPolicy.TARGET_REJECT -> 1
                else -> groups.indexOfFirst { it.id == policy.targetId }.let { if (it < 0) 0 else it + 2 }
            }
            target.setSelection(targetIndex)
            val detail = TextView(this@StrategyManagerActivity).apply {
                text = if (conditions.isEmpty()) "尚未设置匹配条件" else conditions.joinToString("\n") {
                    "${it.matcherType}: ${it.matcherValue}"
                }
                textSize = 13f
                setPadding(0, dp(8), 0, dp(8))
            }
            val form = LinearLayout(this@StrategyManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), 0)
                addView(label("策略名称")); addView(name)
                addView(label("流量目标")); addView(target)
                addView(label("优先级")); addView(priority)
                addView(label("当前匹配条件")); addView(detail)
            }
            val dialog = AlertDialog.Builder(this@StrategyManagerActivity).setTitle("编辑策略：${policy.name}")
                .setView(form)
                .setNegativeButton("删除") { _, _ ->
                    AlertDialog.Builder(this@StrategyManagerActivity)
                        .setMessage("确定删除策略“${policy.name}”？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除") { _, _ ->
                            scope.launch {
                                database.openRoutingPolicyDao().delete(policy.id)
                                refresh(apply = true)
                            }
                        }.show()
                }
                .setNeutralButton(if (policy.enabled) "停用" else "启用") { _, _ ->
                    scope.launch {
                        database.openRoutingPolicyDao().setEnabled(policy.id, !policy.enabled)
                        refresh(apply = true)
                    }
                }
                .setPositiveButton("保存") { _, _ ->
                    val selectedTarget = policyTarget(target.selectedItemPosition, groups)
                    val updated = policy.copy(
                        name = name.text.toString().trim().ifBlank { policy.name },
                        targetType = selectedTarget.first,
                        targetId = selectedTarget.second,
                        priority = priority.text.toString().toIntOrNull() ?: policy.priority,
                        updatedAt = System.currentTimeMillis()
                    )
                    scope.launch {
                        try {
                            StrategyConfigurationRepository(database).savePolicy(updated, conditions)
                            toast("策略已更新")
                            refresh(apply = true)
                        } catch (error: Exception) {
                            toast("保存失败：${error.message}")
                        }
                    }
                }.create()
            dialog.show()
        }
    }

    private fun targetOptions(groups: List<StrategyGroup>) =
        listOf("直连 DIRECT", "拒绝 REJECT") + groups.map { "策略组 · ${it.name}" }

    private fun policyTarget(index: Int, groups: List<StrategyGroup>): Pair<String, String?> = when (index) {
        0 -> RoutingPolicy.TARGET_DIRECT to null
        1 -> RoutingPolicy.TARGET_REJECT to null
        else -> RoutingPolicy.TARGET_STRATEGY_GROUP to groups[index - 2].id
    }

    private fun formView(
        labels: List<String>,
        fields: List<EditText>,
        vararg extras: View
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), 0)
        labels.forEachIndexed { index, text ->
            addView(label(text))
            if (index < fields.size) addView(fields[index])
        }
        extras.forEach { addView(it) }
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        alpha = 0.75f
        setPadding(0, dp(6), 0, dp(2))
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

    private fun edit(hint: String) = EditText(this).apply {
        this.hint = hint
        isSingleLine = true
    }

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
