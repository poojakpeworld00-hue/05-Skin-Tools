package com.fffemotes.skintools.launcher.gadgets

import android.content.Context
import android.graphics.Color

private const val WIDGET_PREFS = "widget_prefs"
private const val DEFAULT_BACKGROUND_COLOR = 0x33000000

internal fun Context.pullWidgetBackdropTint(appWidgetId: Int) =
    getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        .getInt("widget_${appWidgetId}_bg_color", DEFAULT_BACKGROUND_COLOR)

internal fun Context.pullWidgetCaptionTint(appWidgetId: Int) =
    getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        .getInt("widget_${appWidgetId}_text_color", Color.WHITE)
