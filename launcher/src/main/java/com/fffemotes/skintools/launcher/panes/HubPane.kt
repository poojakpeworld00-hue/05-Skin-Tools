package com.fffemotes.skintools.launcher.panes

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.viewbinding.ViewBinding
import com.fffemotes.skintools.launcher.screens.XHubStage

abstract class HubPane<BINDING : ViewBinding>(
    context: Context,
    attributeSet: AttributeSet
) : RelativeLayout(context, attributeSet) {
    protected var activity: XHubStage? = null
    protected lateinit var binding: BINDING

    abstract fun setupFragment(activity: XHubStage)
}
