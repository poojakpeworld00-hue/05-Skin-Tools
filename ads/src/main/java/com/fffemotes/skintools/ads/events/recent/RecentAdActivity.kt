package com.fffemotes.skintools.ads.events.recent

import android.view.LayoutInflater
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.databinding.ScreenRecentAdBinding
import com.fffemotes.skintools.ads.events.FeatureFrameActivity

/** The ad screen a Recents press leads back to (RC `launcher.play_store_return`: bottom_ad, exit_ad on close). */
class RecentAdActivity : FeatureFrameActivity<ScreenRecentAdBinding>() {

    override fun inflateBinding(inflater: LayoutInflater) = ScreenRecentAdBinding.inflate(inflater)

    override val screenName = "recents_event"

    private var leaving = false

    override fun initView() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyInsets()
        binding.recentShimmerVw.visibility = View.GONE // BottomAdLoader brings its own shimmer
        Ads.bottom(this, binding.recentAdFrameVw, Ads.PLAY_STORE_RETURN)
        binding.recentContinueVw.setOnClickListener {
            Analytics.click("continue")
            leave()
        }
    }

    override fun performBack() = leave()

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.recentContentVw) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun leave() {
        if (leaving) return
        leaving = true
        Ads.exit(this, Ads.PLAY_STORE_RETURN) { close() }
    }

    private fun close() {
        if (isFinishing || isDestroyed) return
        finish()
    }
}
