package com.fffemotes.skintools.master.presentation.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.fffemotes.skintools.master.databinding.ItemOptionGridBinding
import com.fffemotes.skintools.master.databinding.ItemOptionRowBinding
import com.fffemotes.skintools.master.presentation.common.pop
import com.intuit.sdp.R as SdpR

/**
 * Options of one step as icon rows or image cards; selection is owned by the ViewModel and pushed in via [submitSelection].
 * Items fade up in a stagger the first time they appear, as in the design. With [inlineAd] an ad slot sits after the
 * second option (design `adAfter`); it gets its container once. [cta] (the step's Continue button) then sits right
 * under that ad instead of pinned at the bottom.
 */
class OptionAdapter(
    private val step: Step,
    private var selected: Set<Int>,
    private val inlineAd: ((FrameLayout) -> Unit)? = null,
    private val cta: View? = null,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var entered = -1
    private val adAt = if (inlineAd != null) minOf(AD_AFTER, step.opts.size) else -1

    private val ctaAt = if (adAt >= 0 && cta != null) adAt + 1 else -1

    /** A full-width row: the ad or the Continue button under it. */
    fun isAd(position: Int) = position == adAt || position == ctaAt

    /** Option index of an adapter position (positions after the ad and the button shift). */
    private fun optionAt(position: Int) = position - listOf(adAt, ctaAt).count { it in 0 until position }

    fun submitSelection(selection: Set<Int>) {
        if (selection == selected) return
        selected = selection
        // Payload: rows only flip their selected state in place. A full rebind ran a change animation per tap, and fast
        // taps cancelled it mid-layout (our animate().cancel()), crashing RecyclerView with "Tmp detached view".
        notifyItemRangeChanged(0, itemCount, SELECTION)
    }

    // Full rebinds (onboarding's in-place language change) must not crossfade either: bind cancels row animations.
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty() || holder !is Holder) return onBindViewHolder(holder, position)
        holder.itemView.isSelected = optionAt(position) in selected
    }

    class Holder(val binding: androidx.viewbinding.ViewBinding) : RecyclerView.ViewHolder(binding.root)

    private class AdHolder(val slot: FrameLayout) : RecyclerView.ViewHolder(slot)

    override fun getItemCount() = step.opts.size + listOf(adAt, ctaAt).count { it >= 0 }

    override fun getItemViewType(position: Int) = when (position) {
        adAt -> TYPE_AD
        ctaAt -> TYPE_CTA
        else -> TYPE_OPTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_CTA) return AdHolder(FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        if (viewType == TYPE_AD) {
            val slot = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    // The ad layout brings its own 7sdp side padding: pull it out so the card lines up with the rows.
                    .apply { val m = resources.getDimensionPixelSize(SdpR.dimen._minus3sdp); setMargins(m, 0, m, 0) }
            }
            return AdHolder(slot).also { inlineAd?.invoke(slot) }
        }
        val inflater = LayoutInflater.from(parent.context)
        return Holder(
            if (step.cards) ItemOptionGridBinding.inflate(inflater, parent, false).also { it.root.clipToOutline = true }
            else ItemOptionRowBinding.inflate(inflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, adapterPosition: Int) {
        if (holder is AdHolder) {
            if (adapterPosition == ctaAt) cta?.let { moveInto(it, holder.slot) }
            return
        }
        val position = optionAt(adapterPosition)
        val opt = step.opts[position]
        val ctx = holder.itemView.context
        val name = ctx.getString(opt.name)
        val sub = opt.sub(ctx)
        when (val b = (holder as Holder).binding) {
            is ItemOptionGridBinding -> {
                b.well.layoutParams.height = ctx.resources.getDimensionPixelSize(step.imageHeight)
                b.image.setImageResource(opt.image)
                b.image.scaleType = step.scale
                b.watermark.text = opt.glyph
                val align = if (step.centered) View.TEXT_ALIGNMENT_CENTER else View.TEXT_ALIGNMENT_VIEW_START
                b.name.textAlignment = align
                b.subText.textAlignment = align
                b.name.text = name
                b.subText.text = sub
                b.subText.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
            }
            is ItemOptionRowBinding -> {
                b.image.visibility = if (opt.image != 0) View.VISIBLE else View.GONE
                if (opt.image != 0) b.image.setImageResource(opt.image)
                b.glyph.text = if (opt.image == 0) opt.glyph else ""
                b.watermark.text = opt.glyph
                b.name.text = name
                b.subText.text = sub
                b.subText.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
            }
        }
        holder.itemView.isSelected = position in selected
        holder.itemView.contentDescription = if (sub.isEmpty()) name else "$name, $sub"
        holder.itemView.setOnClickListener {
            if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
            val p = optionAt(holder.bindingAdapterPosition)
            if (p !in selected) it.pop()
            onClick(p)
        }
        // A recycled row can carry a fade that was cut short (alpha 0): finish it before this row is shown again.
        holder.itemView.animate().cancel()
        if (adapterPosition > entered) {
            entered = adapterPosition
            holder.itemView.apply {
                alpha = 0f
                translationY = resources.getDimension(SdpR.dimen._12sdp)
                animate().alpha(1f).translationY(0f).setStartDelay(50L + adapterPosition * 35L).setDuration(380)
                    .setInterpolator(DecelerateInterpolator(2f)).start()
            }
        } else {
            holder.itemView.alpha = 1f
            holder.itemView.translationY = 0f
        }
    }

    /** The one Continue view moves into whichever holder shows its row; it keeps its own listeners and state. */
    private fun moveInto(view: View, slot: FrameLayout) {
        if (view.parent === slot) return
        (view.parent as? ViewGroup)?.removeView(view)
        val res = slot.resources
        val side = res.getDimensionPixelSize(SdpR.dimen._4sdp)
        val gap = res.getDimensionPixelSize(SdpR.dimen._8sdp)
        slot.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(side, gap, side, gap) })
    }

    private companion object {
        const val TYPE_OPTION = 0
        const val TYPE_AD = 1
        const val TYPE_CTA = 2
        const val AD_AFTER = 2
        val SELECTION = Any()
    }
}
