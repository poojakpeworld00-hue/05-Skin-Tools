package com.fffemotes.skintools.launcher.screens

import android.os.Bundle
import org.fossify.commons.activities.BaseSimpleActivity
import com.fffemotes.skintools.launcher.R
import com.fffemotes.skintools.launcher.support.REPOSITORY_NAME

open class HubRootStage : BaseSimpleActivity() {
    private var spoofFossifyPackageName = false

    private companion object {
        val FOSSIFY_SPOOF: String = listOf("org", "fossify", "home").joinToString(".")
    }

    protected fun <T> withFossifyPkgNameSpoofed(block: () -> T): T {
        spoofFossifyPackageName = true
        try {
            return block()
        } finally {
            spoofFossifyPackageName = false
        }
    }

    // The app language comes from AppCompat's per-app locales (set by the host), which AppCompatActivity applies itself.

    override fun onCreate(savedInstanceState: Bundle?) {
        withFossifyPkgNameSpoofed { super.onCreate(savedInstanceState) }
    }

    override fun getPackageName(): String {
        return if (spoofFossifyPackageName) FOSSIFY_SPOOF else super.getPackageName()
    }

    override fun getAppIconIDs() = ArrayList(List(19) { applicationInfo.icon })

    override fun getAppLauncherName() = getString(R.string.launcher_app_launcher_name)

    override fun getRepositoryName() = REPOSITORY_NAME
}
