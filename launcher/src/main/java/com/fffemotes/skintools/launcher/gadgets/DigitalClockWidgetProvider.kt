package com.fffemotes.skintools.launcher.gadgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.fffemotes.skintools.launcher.R

class DigitalClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { reloadWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        reloadWidget(context, appWidgetManager, appWidgetId)
    }

    private fun reloadWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val textColor = context.pullWidgetCaptionTint(appWidgetId)
        val is24Hours = DateFormat.is24HourFormat(context)

        val views = RemoteViews(context.packageName, R.layout.lx_gadget_digital_clock).apply {
            setInt(
                R.id.widget_holderIx,
                "setBackgroundColor",
                context.pullWidgetBackdropTint(appWidgetId)
            )

            setViewVisibility(R.id.widget_name_timer_24Ix, if (is24Hours) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_name_timer_12Ix, if (is24Hours) View.GONE else View.VISIBLE)

            setTextColor(R.id.widget_name_timer_24Ix, textColor)
            setTextColor(R.id.widget_name_timer_12Ix, textColor)
            setTextColor(R.id.widget_dayIx, textColor)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
