package com.fffemotes.skintools.master.presentation.settings

import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import android.os.Bundle
import com.fffemotes.skintools.master.presentation.common.enableDarkEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.databinding.ActivityLanguageBinding
import com.fffemotes.skintools.master.presentation.common.appViewModel
import com.fffemotes.skintools.master.presentation.common.collectWhenStarted
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.onboarding.OptionAdapter
import com.fffemotes.skintools.master.presentation.onboarding.languageStep

/** Language picker opened from Settings; Apply switches the app language and returns to Settings. */
class LanguageActivity : AppCompatActivity() {
    private val viewModel by appViewModel { handle -> LanguageViewModel(settingsRepository, handle) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        val b = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.header.back.setOnClickListener { finish() }
        b.header.title.setText(R.string.language)

        val adapter = OptionAdapter(languageStep(viewModel.languages), setOf(viewModel.selected.value)) { viewModel.select(it) }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.list.scrollToPosition(viewModel.selected.value)
        collectWhenStarted(viewModel.selected) { adapter.submitSelection(setOf(it)) }

        b.apply.setOnClickListener {
            Analytics.click("apply", viewModel.languages[viewModel.selected.value].tag)
            Ads.exit(this, PLACE) {
                // Finish first: the locale change then recreates only Settings (and Home) behind us, in the new language.
                finish()
                viewModel.apply()
            }
        }
        Ads.bottom(this, b.adSlot, PLACE)
    }

    override fun onResume() {
        super.onResume()
        Screens.show("language")
    }

    private companion object {
        const val PLACE = "screens.language"
    }
}
