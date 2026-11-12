package com.github.kr328.clash.design.component

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.view.VerticalScrollableHost

class ProxyPageFactory(private val config: ProxyViewConfig) {
    class Holder(
        val recyclerView: RecyclerView,
        val root: View,
    ) : RecyclerView.ViewHolder(root)

    private val childrenPool = RecyclerView.RecycledViewPool()

    fun newInstance(): Holder {
        val root = VerticalScrollableHost(config.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val recyclerView = RecyclerView(config.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(recyclerView)

        recyclerView.apply {
            // Proxy choices are always displayed as a single vertical list.
            // The previous grid layout made node selection depend on a
            // horizontal-card density preference, which conflicts with the
            // Clash-Zero interaction model.
            layoutManager = LinearLayoutManager(config.context)

            setRecycledViewPool(childrenPool)

            clipToPadding = false
        }

        return Holder(recyclerView, root).apply {
            root.tag = this
        }
    }

    fun fromRoot(root: View): Holder {
        return root.tag!! as Holder
    }
}
