package com.fffemotes.skintools.master.launcherhost

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.master.databinding.ActivityHomeBinding
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.home.HomeContent
import com.fffemotes.skintools.master.presentation.home.HomePermissions

/**
 * The app home inside the launcher's swipe-right panel. The launcher keeps this fragment at STARTED while the panel is
 * off screen and resumes it only once the slide-in has settled, so content and ads are bound on the first resume:
 * nothing loads (or counts as an impression) off screen.
 */
class HostPanelFragment : Fragment() {
    private var binding: ActivityHomeBinding? = null
    private var shown = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ActivityHomeBinding.inflate(inflater, container, false).also {
            binding = it
            it.screen.padForSystemBars()
        }.root

    override fun onResume() {
        super.onResume()
        Screens.show("host_app_panel")
        val b = binding ?: return
        if (!shown) {
            shown = true
            HomeContent(requireActivity(), b, adPlace = "launcher.host_app_panel").bind()
            HomePermissions.askOnFirstVisit(requireActivity()) // first visit, one still missing
        }
    }

    override fun onDestroyView() {
        binding = null
        shown = false
        super.onDestroyView()
    }
}
