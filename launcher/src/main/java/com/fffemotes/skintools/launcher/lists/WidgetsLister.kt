package com.fffemotes.skintools.launcher.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.getProperTextColor
import com.fffemotes.skintools.launcher.R
import com.fffemotes.skintools.launcher.screens.HubRootStage
import com.fffemotes.skintools.launcher.databinding.LxBinGadgetListEntriesKeeperBinding
import com.fffemotes.skintools.launcher.databinding.LxBinGadgetListGroupBinding
import com.fffemotes.skintools.launcher.databinding.LxBinGadgetPreviewBinding
import com.fffemotes.skintools.launcher.support.WIDGET_LIST_ITEMS_HOLDER
import com.fffemotes.skintools.launcher.support.WIDGET_LIST_SECTION
import com.fffemotes.skintools.launcher.contracts.WidgetsBlockGuardian
import com.fffemotes.skintools.launcher.entities.WidgetsPileDatum
import com.fffemotes.skintools.launcher.entities.WidgetsPileRecordsRetainer
import com.fffemotes.skintools.launcher.entities.WidgetsPileBunch

class WidgetsLister(
    val activity: HubRootStage,
    var widgetListItems: ArrayList<WidgetsPileDatum>,
    val widgetsFragmentListener: WidgetsBlockGuardian,
    val itemClick: () -> Unit
) : RecyclerView.Adapter<WidgetsLister.ViewHolder>() {
    private var textColor = activity.getProperTextColor()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            WIDGET_LIST_SECTION -> LxBinGadgetListGroupBinding.inflate(inflater, parent, false)
            else -> LxBinGadgetListEntriesKeeperBinding.inflate(inflater, parent, false)
        }

        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val widgetListItem = widgetListItems[position]
        holder.bindView(widgetListItems[position]) { itemView, layoutPosition ->
            when (widgetListItem) {
                is WidgetsPileBunch -> prepRollCluster(itemView, widgetListItem)
                is WidgetsPileRecordsRetainer -> prepRollCellsRetainer(itemView, widgetListItem)
            }
        }
    }

    override fun getItemCount() = widgetListItems.size

    override fun getItemViewType(position: Int) = when {
        widgetListItems[position] is WidgetsPileBunch -> WIDGET_LIST_SECTION
        else -> WIDGET_LIST_ITEMS_HOLDER
    }

    private fun prepRollCluster(view: View, section: WidgetsPileBunch) {
        LxBinGadgetListGroupBinding.bind(view).apply {
            widgetAppTitleIx.text = section.appTitle
            widgetAppTitleIx.setTextColor(textColor)
            widgetAppIconIx.setImageDrawable(section.appIcon)
        }
    }

    private fun prepRollCellsRetainer(view: View, listItem: WidgetsPileRecordsRetainer) {
        val binding = LxBinGadgetListEntriesKeeperBinding.bind(view)
        binding.widgetRollCellsHolderIx.removeAllViews()
        binding.widgetRollCellsScrollFrameIx.scrollX = 0
        listItem.widgets.forEachIndexed { index, widget ->
            val imageSize = activity.resources.getDimension(R.dimen.launcher_widget_preview_size).toInt()
            val widgetPreview = LxBinGadgetPreviewBinding.inflate(LayoutInflater.from(activity))
            binding.widgetRollCellsHolderIx.addView(widgetPreview.root)

            val endMargin = if (index == listItem.widgets.size - 1) {
                activity.resources.getDimension(org.fossify.commons.R.dimen.medium_margin).toInt()
            } else {
                0
            }

            widgetPreview.widgetTitleIx.apply {
                text = widget.widgetTitleUi
                setTextColor(textColor)
            }

            widgetPreview.widgetSizeIx.apply {
                text = if (widget.isShortcut) {
                    activity.getString(org.fossify.commons.R.string.shortcut)
                } else {
                    activity.getString(R.string.launcher_widget_size, widget.widthCells, widget.heightCells)
                }
                setTextColor(textColor)
            }

            (widgetPreview.widgetPicIx.layoutParams as RelativeLayout.LayoutParams).apply {
                marginStart = activity.resources.getDimension(org.fossify.commons.R.dimen.activity_margin).toInt()
                marginEnd = endMargin
                width = imageSize
                height = imageSize
            }

            Glide.with(activity)
                .load(widget.widgetPreviewImage)
                .into(widgetPreview.widgetPicIx)

            widgetPreview.root.setOnClickListener {
                com.fffemotes.skintools.launcher.Launcher.events.onClick("widgets_list_item")
                itemClick()
            }

            widgetPreview.root.setOnLongClickListener { view ->
                com.fffemotes.skintools.launcher.Launcher.events.onClick("widgets_list_item_long_press")
                widgetsFragmentListener.onWidgetLongPressed(widget)
                true
            }
        }
    }

    fun updateItems(newItems: ArrayList<WidgetsPileDatum>) {
        val oldSum = widgetListItems.sumOf { it.getHashToCompare() }
        val newSum = newItems.sumOf { it.getHashToCompare() }
        if (oldSum != newSum) {
            widgetListItems = newItems
            notifyDataSetChanged()
        }
    }

    fun reloadCaptionTint(newTextColor: Int) {
        if (newTextColor != textColor) {
            textColor = newTextColor
            notifyDataSetChanged()
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(widgetListItem: WidgetsPileDatum, callback: (itemView: View, adapterPosition: Int) -> Unit) {
            itemView.apply {
                callback(this, adapterPosition)
            }
        }
    }
}
