package com.fffemotes.skintools.ads.render
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.fffemotes.skintools.ads.entity.HouseAdCard
import com.fffemotes.skintools.ads.policy.AdSettingsVault
import com.fffemotes.skintools.ads.R
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.host.AdEvents

/** [place] = the RC place key this custom ad runs for (`ad` events). */
class HouseAdDirector(private val place: String = AdEvents.place) {
    companion object {
        private var cachedAds: List<HouseAdCard>? = null

        fun clearCache() {
            cachedAds = null
        }
    }

    enum class HouseAdStyle {
        BIG_NATIVE,
        MID_NATIVE,
        BANNER,
        FULLSCREEN_NATIVE
    }

    fun fetchHouseAd(
        context: Context,
        container: FrameLayout,
        type: HouseAdStyle,
        backupImage: ImageView? = null,
        backupLayout: LinearLayout? = null,
        onFail: (() -> Unit)? = null
    ) {
        if (context.hostActivityGone()) {
            fallback(container, backupImage, backupLayout, onFail)
            return
        }

        val pref = AdSettingsVault.getInstance(context)

        if (!pref.getBoolean("IsCustomADS")) {
            fallback(container, backupImage, backupLayout, onFail)
            return
        }

        val ads: List<HouseAdCard> = if (cachedAds != null) {
            cachedAds!!
        } else {
            val json = pref.getString("CUSTOM_ADS", null)
            if (json.isNullOrEmpty()) {
                fallback(container, backupImage, backupLayout, onFail)
                return
            }
            try {
                val parsed: List<HouseAdCard> = Gson().fromJson(json, object : TypeToken<List<HouseAdCard>>() {}.type)
                cachedAds = parsed
                parsed
            } catch (e: Exception) {
                fallback(container, backupImage, backupLayout, onFail)
                return
            }
        }

        if (ads.isEmpty()) {
            fallback(container, backupImage, backupLayout, onFail)
            return
        }

        val ad = ads.random()

        val layoutId = when (type) {
            HouseAdStyle.BIG_NATIVE -> R.layout.house_ad_card_a
            HouseAdStyle.MID_NATIVE -> R.layout.house_ad_card_b
            HouseAdStyle.BANNER -> R.layout.house_ad_strip
            HouseAdStyle.FULLSCREEN_NATIVE -> R.layout.house_ad_fullpage
        }
        val view = try {
            LayoutInflater.from(container.context).inflate(layoutId, container, false)
        } catch (e: Exception) {
            fallback(container, backupImage, backupLayout, onFail)
            return
        }

        applyTheme(context, view, pref)
        bindData( view, ad)
        wireClicks(view)

        container.post {
            if (context.hostActivityGone()) {
                container.visibility = View.GONE
                return@post
            }

            container.findFocus()?.clearFocus()
            container.removeAllViews()
            container.addView(view)
            container.visibility = View.VISIBLE
            AdEvents.send(AdEvents.CUSTOM, "show", place)

            backupImage?.visibility = View.GONE
            backupLayout?.visibility = View.GONE
        }
    }

    private fun applyTheme(context: Context, view: View, pref: AdSettingsVault) {
        try {
            val bgColor = pref.getString("NativeBgColor")
            val btnColor = pref.getString("NativebtnColor")
            val txtColor = pref.getString("NativetxtColor")
            val btnTxtColor = pref.getString("NativebtntxtColor")

            AdFrameStyling.paintCardBackground(context, view.findViewById<LinearLayout?>(R.id.mainlnr), bgColor)

            val ctaText = AdFrameStyling.ctaTextColor(context, btnTxtColor)
            view.findViewById<TextView?>(R.id.btntext)?.let {
                AdFrameStyling.paintCtaBackground(context, it, btnColor)
                it.setTextColor(ctaText)
            }
            view.findViewById<TextView?>(R.id.only_banner_button)?.let {
                AdFrameStyling.paintCtaBackground(context, it, btnColor)
                it.setTextColor(ctaText)
            }

            val headline = AdFrameStyling.titleColor(context, txtColor)
            val body = AdFrameStyling.bodyColor(context, txtColor)
            view.findViewById<TextView?>(R.id.titileText)?.setTextColor(headline)
            view.findViewById<TextView?>(R.id.Texttext)?.setTextColor(body)

            view.findViewById<TextView?>(R.id.only_banner_title)?.setTextColor(headline)
            view.findViewById<TextView?>(R.id.only_banner_desc)?.setTextColor(body)
        } catch (_: Exception) {
        }
    }

    private fun bindData(view: View, ad: HouseAdCard) {
        view.findViewById<TextView?>(R.id.titileText)?.text = ad.title
        view.findViewById<TextView?>(R.id.Texttext)?.text = ad.description
        view.findViewById<TextView?>(R.id.btntext)?.text = ad.buttonText

        view.findViewById<TextView?>(R.id.only_banner_title)?.text = ad.title
        view.findViewById<TextView?>(R.id.only_banner_desc)?.text = ad.description
        view.findViewById<TextView?>(R.id.only_banner_button)?.text = ad.buttonText

        view.findViewById<ImageView?>(R.id.gif_image)?.let { loadIconSafely(it, ad.icon) }
        view.findViewById<ImageView?>(R.id.only_banner_logo)?.let { loadIconSafely(it, ad.icon) }
        view.findViewById<ImageView?>(R.id.custom_native)?.let { loadIconSafely(it, ad.bannerImage) }
    }

    private fun Context.locateHostActivity(): Activity? {
        var ctx: Context = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun Context.hostActivityGone(): Boolean {
        val act = this as? Activity ?: locateHostActivity() ?: return false
        if (act.isFinishing) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && act.isDestroyed
    }

    private fun loadIconSafely(imageView: ImageView, url: String?) {
        if (url.isNullOrEmpty()) return
        if (imageView.context.hostActivityGone()) return
        try {
            Glide.with(imageView).load(url).into(imageView)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun wireClicks(view: View) {
        val click = View.OnClickListener {
            Analytics.click("house_ad_cta")
            AdEvents.send(AdEvents.CUSTOM, "click", place)
            pushSponsorLink(view.context)
        }

        // Only the CTA opens the sponsor link (no whole-card click area).
        view.findViewById<View?>(R.id.btntext)?.setOnClickListener(click)
        view.findViewById<View?>(R.id.only_banner_button)?.setOnClickListener(click)
    }

    private fun fallback(
        container: FrameLayout,
        img: ImageView?,
        layout: LinearLayout?,
        onFail: (() -> Unit)?
    ) {
        container.post {
            if (container.context.hostActivityGone()) {
                container.visibility = View.GONE
                return@post
            }

            container.findFocus()?.clearFocus()
            container.removeAllViews()
            container.visibility = View.GONE

            img?.visibility = View.VISIBLE
            layout?.visibility = View.VISIBLE

            onFail?.invoke()
        }
    }
}
