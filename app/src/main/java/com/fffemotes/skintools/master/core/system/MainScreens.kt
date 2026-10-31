package com.fffemotes.skintools.master.core.system

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.fffemotes.skintools.ads.ExitAdRunner
import com.fffemotes.skintools.master.App
import com.fffemotes.skintools.master.presentation.home.HomeActivity
import com.fffemotes.skintools.master.presentation.onboarding.OnboardingActivity
import com.fffemotes.skintools.master.presentation.splash.SplashActivity

/**
 * The reference app's single MainActivity, mapped onto FFF's screens: before onboarding is done the "main screen" is
 * [OnboardingActivity], after it [HomeActivity]. A task of ours that started on one of [classes] is an app task (not the
 * launcher's home task).
 */
object MainScreens {
    private val classes = setOf(SplashActivity::class.java.name, OnboardingActivity::class.java.name, HomeActivity::class.java.name)

    fun isMain(activity: Activity) = activity is OnboardingActivity || activity is HomeActivity

    fun isAppTaskBase(className: String?) = className in classes

    fun onboardingDone(context: Context) = (context.applicationContext as App).container.settingsRepository.isOnboarded()

    private fun mainClass(context: Context): Class<out Activity> =
        if (onboardingDone(context)) HomeActivity::class.java else OnboardingActivity::class.java

    /** Our launcher home through Android's own Home intent, so it is the one in the system's home stack. */
    fun launcherHomeIntent(context: Context): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Brings the task of our app screens to the front as it is, whatever is on top of it. */
    fun appTaskToFront(context: Context): Boolean {
        val task = context.getSystemService(ActivityManager::class.java)?.appTasks?.firstOrNull {
            val info = it.taskInfo
            isAppTaskBase(info?.baseIntent?.component?.className) || isAppTaskBase(info?.baseActivity?.className)
        } ?: return false
        return runCatching { task.moveToFront() }.isSuccess
    }

    fun open(context: Context, systemKey: String? = null) {
        // An exit ad (its ad screen or link tabs) is on top of our screen: bring the task back as it is, so the ad finishes.
        if (ExitAdRunner.busy() && appTaskToFront(context)) return
        context.startActivity(
            Intent(context, mainClass(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .apply { if (systemKey != null) putExtra(SystemKeyTarget.EXTRA_SYSTEM_KEY, systemKey) }
        )
    }
}
