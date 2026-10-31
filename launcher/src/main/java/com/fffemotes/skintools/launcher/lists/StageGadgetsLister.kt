package com.fffemotes.skintools.launcher.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fffemotes.skintools.launcher.R
import com.fffemotes.skintools.launcher.support.SymbolLoader
import com.fffemotes.skintools.launcher.entities.GadgetHub

class StageGadgetsLister(
    private val layoutRes: Int,
    private val itemClick: (GadgetHub) -> Unit,
) : ListAdapter<GadgetHub, StageGadgetsLister.ViewHolder>(GadgetHubDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(launcher: GadgetHub) {
            itemView.findViewById<TextView>(R.id.cell_headingAltIx).text = launcher.title

            // the icons are already loaded in memory by the drawer, no need for Glide here; one that
            // is not yet (a cold start) is decoded on its own instead of waiting for the full scan
            val icon = itemView.findViewById<ImageView>(R.id.box_iconIx)
            val ready = launcher.drawable ?: SymbolLoader.cached(launcher.packageName, launcher.activityName)
            icon.setImageDrawable(ready)
            if (ready == null) {
                val wanted = launcher.packageName + "/" + launcher.activityName
                icon.tag = wanted
                SymbolLoader.load(itemView.context, launcher.packageName, launcher.activityName) { loaded ->
                    if (icon.tag == wanted) icon.setImageDrawable(loaded)
                }
            } else {
                icon.tag = null
            }
            itemView.setOnClickListener { itemClick(launcher) }
        }
    }
}
