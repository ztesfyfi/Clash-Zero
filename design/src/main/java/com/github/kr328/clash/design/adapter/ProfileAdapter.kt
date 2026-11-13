package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.View
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.AdapterProfileBinding
import com.github.kr328.clash.design.model.ProfileNodeItem
import com.github.kr328.clash.design.model.ProfilePageState
import com.github.kr328.clash.design.ui.ObservableCurrentTime
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.service.model.Profile
import java.util.UUID

class ProfileAdapter(
    private val context: Context,
    private val onClicked: (Profile) -> Unit,
    private val onMenuClicked: (Profile) -> Unit,
    private val onNodeClicked: (Profile, ProfileNodeItem) -> Unit,
    private val onManageGroups: () -> Unit,
    private val onTestProfile: (Profile) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
    class Holder(
        val binding: AdapterProfileBinding,
        val nodeAdapter: ProfileNodeAdapter,
    ) : RecyclerView.ViewHolder(binding.root)

    private val currentTime = ObservableCurrentTime()
    var profiles: List<Profile> = emptyList()
    val states = ProfilePageState()
    private val nodes = mutableMapOf<UUID, List<ProfileNodeItem>>()
    private val expanded = mutableMapOf<UUID, Int>()

    fun setNodes(value: Map<UUID, List<ProfileNodeItem>>) {
        nodes.clear()
        nodes.putAll(value)
        notifyDataSetChanged()
    }

    fun updateElapsed() {
        currentTime.update()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterProfileBinding
            .inflate(context.layoutInflater, parent, false)
            .also { it.currentTime = currentTime }
        lateinit var holder: Holder
        val nodeAdapter = ProfileNodeAdapter(context) { node ->
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onNodeClicked(profiles[position], node)
        }
        binding.nodeList.layoutManager = LinearLayoutManager(context)
        binding.nodeList.adapter = nodeAdapter
        binding.nodeSummary.setOnClickListener { onManageGroups() }
        binding.nodeTest.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onTestProfile(profiles[position])
        }
        holder = Holder(binding, nodeAdapter)
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val profile = profiles[position]
        val binding = holder.binding
        binding.profile = profile
        binding.setClicked {
            val stage = (expanded[profile.uuid] ?: 0)
            expanded[profile.uuid] = when (stage) { 0 -> 1; 1 -> 2; else -> 0 }
            notifyItemChanged(position)
        }
        binding.setMenu { onMenuClicked(profile) }

        val stage = expanded[profile.uuid] ?: 0
        val profileNodes = nodes[profile.uuid].orEmpty()
        binding.nodeSummary.visibility = if (stage == 0) View.GONE else View.VISIBLE
        binding.nodeList.visibility = if (stage == 2) View.VISIBLE else View.GONE
        binding.nodeTest.visibility = if (stage > 0) View.VISIBLE else View.GONE
        val good = profileNodes.count { it.delayState == "GOOD" }
        val slow = profileNodes.count { it.delayState == "SLOW" }
        val error = profileNodes.count { it.delayState == "ERROR" }
        val tested = profileNodes.count { it.delayState == "TESTED" }
        val failed = profileNodes.count { it.delayState == "FAILED" }
        val untested = profileNodes.count { it.delayState == "UNTESTED" }
        val summary = "节点总数：${profileNodes.size} · 正常：${good} · 较慢：${slow} · 异常：${error} · 已测速：${tested} · 失败/超时：${failed} · 未测试：${untested} · 点击管理分组"
        val styled = SpannableString(summary)
        fun tint(label: String, color: Int) {
            val start = summary.indexOf(label)
            if (start >= 0) styled.setSpan(ForegroundColorSpan(color), start, start + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tint("正常：${good}", Color.rgb(46, 125, 50))
        tint("较慢：${slow}", Color.rgb(245, 158, 11))
        tint("异常：${error}", Color.rgb(211, 47, 47))
        tint("失败/超时：${failed}", Color.GRAY)
        tint("未测试：${untested}", Color.GRAY)
        binding.nodeSummary.text = styled
        holder.nodeAdapter.nodes = if (stage == 2) profileNodes else emptyList()
    }

    override fun getItemCount(): Int = profiles.size
}
