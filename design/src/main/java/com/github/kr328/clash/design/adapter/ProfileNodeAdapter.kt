package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.AdapterProfileNodeBinding
import com.github.kr328.clash.design.model.ProfileNodeItem
import com.github.kr328.clash.design.util.layoutInflater

class ProfileNodeAdapter(
    private val context: Context,
    private val onClicked: (ProfileNodeItem) -> Unit,
) : RecyclerView.Adapter<ProfileNodeAdapter.Holder>() {
    class Holder(val binding: AdapterProfileNodeBinding) : RecyclerView.ViewHolder(binding.root)

    var nodes: List<ProfileNodeItem> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(AdapterProfileNodeBinding.inflate(context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val node = nodes[position]
        holder.binding.node = node
        holder.binding.setClicked { onClicked(node) }
    }

    override fun getItemCount(): Int = nodes.size
}
