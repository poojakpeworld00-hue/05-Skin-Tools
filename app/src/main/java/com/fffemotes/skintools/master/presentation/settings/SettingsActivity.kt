package com.fffemotes.skintools.master.presentation.settings

import com.fffemotes.skintools.master.presentation.common.bindScreenAd
import com.fffemotes.skintools.ads.AdKit
import androidx.activity.addCallback
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.master.App
import com.fffemotes.skintools.master.presentation.onboarding.onboardingSteps
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.fffemotes.skintools.launcher.Launcher
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.fffemotes.skintools.master.presentation.common.enableDarkEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.databinding.ActivitySettingsBinding
import com.fffemotes.skintools.master.databinding.ItemToolBinding
import com.fffemotes.skintools.master.presentation.common.appViewModel
import com.fffemotes.skintools.master.presentation.common.collectWhenStarted
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.common.listAd
import com.fffemotes.skintools.master.presentation.common.insertInto

/** Settings: language, plus share, rate, privacy policy and terms. */
class SettingsActivity : AppCompatActivity() {
    private val viewModel by appViewModel { SettingsViewModel(settingsRepository) }
    private lateinit var languageRow: ItemToolBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        val b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        // As the reference: leaving Settings (arrow or Back) runs its `screens.settings` exit ad first.
        val close = { Ads.exit(this, "screens.settings") { finish() } }
        b.header.back.setOnClickListener { close() }
        onBackPressedDispatcher.addCallback(this) { close() }
        b.header.title.setText(R.string.settings)

        // launcher.default_home_banner: offer "Set as default home" while another launcher is the default.
        val launcher = (application as App).container.config.value.launcher
        if (launcher.on && launcher.defaultHomeBanner && !Launcher.isDefaultHome(this)) {
            row(b.appearance, R.drawable.ic_settings, R.string.set_default_home, null) {
                Launcher.requestDefaultHome(this) { granted -> if (granted) recreate() }
            }
        }
        languageRow = row(b.appearance, R.drawable.ic_language, R.string.language, "") {
            startActivity(Intent(this, LanguageActivity::class.java))
        }

        // screens.settings list_ad: right under the Language row.
        listAd("screens.settings")?.insertInto(
            b.appearance, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._7sdp),
            at = b.appearance.indexOfChild(languageRow.root) + 1,
        )

        profileRow(b.profile, R.drawable.ic_person, R.string.step_gender, "OB_2")
        profileRow(b.profile, R.drawable.ic_cake, R.string.step_age, "OB_3")

        // Remote Config `settings`: each of these four rows can be turned off per track (and country).
        val rows = (application as App).container.config.value.settingsRows
        if (rows.share) row(b.about, R.drawable.ic_share, R.string.share_app, getString(R.string.share_app_sub)) { share() }
        if (rows.rate) row(b.about, R.drawable.ic_star, R.string.rate_app, getString(R.string.rate_app_sub)) { rate() }
        if (rows.feedback) row(b.about, R.drawable.ic_mail, R.string.feedback, getString(R.string.feedback_sub)) { feedback() }
        if (rows.update) row(b.about, R.drawable.ic_update, R.string.update_app, getString(R.string.update_app_sub)) {
            open(base.updateLink.ifBlank { storeUrl() })
        }
        row(b.about, R.drawable.ic_shield, R.string.privacy_policy, null) { open(base.privacyLink.ifBlank { getString(R.string.privacy_policy_url) }) }
        row(b.about, R.drawable.ic_document, R.string.terms, null) { open(base.termsLink.ifBlank { getString(R.string.terms_url) }) }
        // Ad consent (UMP): users in regions that require it can change their choice here.
        if (AdKit.privacyOptionsRequired()) {
            row(b.about, R.drawable.ic_shield, R.string.privacy_options, null) { AdKit.showPrivacyOptions(this) {} }
        }

        b.version.text = getString(R.string.settings_version, packageManager.getPackageInfo(packageName, 0).versionName)

        collectWhenStarted(viewModel.state) { state ->
            languageRow.sub.text = "${state.language.flag}  ${getString(state.language.nativeName)}"
        }
        bindScreenAd("screens.settings", b.adInline, b.adSlot)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        Screens.show("settings")
    }

    private fun row(parent: ViewGroup, @DrawableRes icon: Int, @StringRes name: Int, sub: String?, onClick: () -> Unit): ItemToolBinding {
        val r = ItemToolBinding.inflate(layoutInflater, parent, true)
        r.icon.setImageResource(icon)
        r.name.setText(name)
        r.sub.text = sub
        r.sub.visibility = if (sub == null) View.GONE else View.VISIBLE
        r.root.setOnClickListener {
            Analytics.click(resources.getResourceEntryName(name))
            onClick()
        }
        return r
    }

    /** Gender / age: the onboarding answer, editable here (single choice from the same options). */
    private fun profileRow(parent: ViewGroup, @DrawableRes icon: Int, @StringRes name: Int, stepKey: String) {
        val settings = (application as App).container.settingsRepository
        val step = onboardingSteps(settings.languages).first { it.key == stepKey }
        val labels = step.opts.map { getString(it.name) }
        fun current() = settings.answer(stepKey)?.firstOrNull() ?: step.defaultSel.firstOrNull()
        lateinit var r: ItemToolBinding
        r = row(parent, icon, name, current()?.let(labels::getOrNull).orEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(step.title)
                .setSingleChoiceItems(labels.toTypedArray(), current() ?: -1) { dialog, which ->
                    settings.setAnswer(stepKey, setOf(which))
                    r.sub.text = labels[which]
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun feedback() {
        val email = getString(R.string.feedback_email)
        if (email.isBlank()) return open("")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
            .putExtra(Intent.EXTRA_SUBJECT, "${getString(R.string.store_name)} ${packageManager.getPackageInfo(packageName, 0).versionName}")
        Ads.markOwnLaunch()
        runCatching { startActivity(intent) }.onFailure {
            Ads.clearOwnLaunch()
            Toast.makeText(this, R.string.link_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun share() {
        val text = getString(R.string.share_message, getString(R.string.store_name), storeUrl())
        Ads.markOwnLaunch() // no resume ad when the user comes back from the share sheet
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }

    private fun rate() {
        try {
            Ads.markOwnLaunch()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: ActivityNotFoundException) {
            open(storeUrl())
        }
    }

    private fun open(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, R.string.link_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            Ads.markOwnLaunch()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Ads.clearOwnLaunch()
            Toast.makeText(this, R.string.link_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun storeUrl() = base.storeLink.ifBlank { "https://play.google.com/store/apps/details?id=$packageName" }

    private val base get() = (application as App).container.config.value.base
}
