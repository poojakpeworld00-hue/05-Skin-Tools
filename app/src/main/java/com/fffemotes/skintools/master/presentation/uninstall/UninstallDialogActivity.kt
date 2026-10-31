package com.fffemotes.skintools.master.presentation.uninstall

import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.master.App
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.data.remote.config.Track

/**
 * The reference app's uninstall as an Android-style dialog (`uninstall_flow.system_dialog`), opened by Uninstall in the
 * launcher's long-press menu on our icon. No ads. OK: paid hides our icon from home and drawer (the app stays installed);
 * organic opens Android's App info page, as the end of [UninstallFlowActivity]. Cancel / Back / outside: nothing.
 */
class UninstallDialogActivity : AppCompatActivity() {
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = (application as App).container.config.value.uninstallFlow
        if (!config.premium || !config.systemDialog) return finish()
        if (savedInstanceState == null) Analytics.step(Analytics.UNINSTALL, "start")
        var ended = false
        // The phone's own alert theme, light or dark with the system, so it looks like that brand's uninstall dialog.
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val theme = if (night) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        dialog = AlertDialog.Builder(this, theme)
            .setTitle(R.string.app_name)
            .setMessage(R.string.un_dialog_message)
            .setNegativeButton(android.R.string.cancel) { _, _ -> Analytics.click("uninstall_cancel") }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ended = true
                Analytics.click("uninstall_ok")
                endUninstall(this, (application as App).container.config.value.track == Track.B)
            }
            .setOnDismissListener {
                if (!ended) Analytics.step(Analytics.UNINSTALL, "cancel")
                finish()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        Screens.show("uninstall_dialog")
    }

    override fun onDestroy() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        super.onDestroy()
    }
}
