package com.fffemotes.skintools.launcher.entities

import android.graphics.drawable.Drawable

data class WidgetsPileBunch(var appTitle: String, var appIcon: Drawable?) : WidgetsPileDatum() {
    override fun getHashToCompare() = pullStringToCompare().hashCode()

    private fun pullStringToCompare(): String {
        return copy(appIcon = null).toString()
    }
}
