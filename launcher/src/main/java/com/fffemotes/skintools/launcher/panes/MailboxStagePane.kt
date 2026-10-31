package com.fffemotes.skintools.launcher.panes

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.launcher.R
import com.fffemotes.skintools.launcher.databinding.LxMessagesScrZoneBinding
import com.fffemotes.skintools.launcher.screens.XHubStage
import timber.log.Timber
import kotlin.math.abs

/**
 * Right-swipe panel holding the host's own home UI ([com.fffemotes.skintools.launcher.api.LauncherHost.createHostPanel]).
 * The fragment is committed early but parked off-screen at STARTED; it is RESUMED only while the panel is
 * open and has finished sliding in, so it shows prompts / loads ads / enables its back handling in onResume.
 */
class MailboxStagePane(
    context: Context,
    attributeSet: AttributeSet,
) : HubPane<LxMessagesScrZoneBinding>(context, attributeSet) {
    private val gestureDetector = GestureDetectorCompat(context, object : SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (velocityX < 0 && abs(velocityX) > abs(velocityY)) {
                activity?.concealMsgsDeck()
                return true
            }
            return false
        }
    })

    private val hostFragment: Fragment?
        get() = activity?.supportFragmentManager?.findFragmentById(R.id.postbox_holderIx)

    override fun setupFragment(activity: XHubStage) {
        this.activity = activity
        this.binding = LxMessagesScrZoneBinding.bind(this)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    fun refresh() {
        val fm = activity?.supportFragmentManager ?: return
        if (fm.isStateSaved || hostFragment != null) return
        val host = Launcher.host() ?: return
        runCatching {
            val fragment = host.createHostPanel()
            fm.beginTransaction()
                .replace(R.id.postbox_holderIx, fragment)
                .setMaxLifecycle(fragment, Lifecycle.State.STARTED)
                .commitNow()
        }.onFailure { Timber.e(it, "LauncherPanel: could not add the host panel") }
    }

    fun warmUp() = refresh()

    /** Off-screen (or sliding out): back to STARTED, so the fragment's on-screen work stops. */
    fun setPanelVisible(visible: Boolean) {
        if (!visible) cap(Lifecycle.State.STARTED)
    }

    /** The slide-in finished: the panel is fully on screen. */
    fun onPanelSettled() = cap(Lifecycle.State.RESUMED)

    private fun cap(state: Lifecycle.State) {
        val fm = activity?.supportFragmentManager ?: return
        val fragment = hostFragment ?: return
        if (fm.isStateSaved) return
        runCatching { fm.beginTransaction().setMaxLifecycle(fragment, state).commitNow() }
            .onFailure { Timber.w(it, "LauncherPanel: could not cap the host panel at $state") }
    }
}
