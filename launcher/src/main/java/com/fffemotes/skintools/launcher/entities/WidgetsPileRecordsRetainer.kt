package com.fffemotes.skintools.launcher.entities

open class WidgetsPileRecordsRetainer(val widgets: ArrayList<GadgetWidget>) : WidgetsPileDatum() {
    override fun getHashToCompare() = widgets.sumOf { it.getHashToCompare() }
}
