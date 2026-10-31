package com.fffemotes.skintools.launcher.listeners

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.fffemotes.skintools.launcher.R

class SecureCallerManagerListener : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.launcher_lock_device_admin_warning)
    }
}
