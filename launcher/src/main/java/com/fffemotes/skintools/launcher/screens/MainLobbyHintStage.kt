package com.fffemotes.skintools.launcher.screens

import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.graphics.Bitmap
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updatePadding
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.launcher.R
import timber.log.Timber

class MainLobbyHintStage : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var switchAnimator: Animator? = null
    private var haloAnimator: Animator? = null
    private var tapStep = 0
    private lateinit var kind: Kind

    enum class Kind(
        val title: Int,
        val description: Int,
        val position: Position,
        val control: Control,
        /** null: stays until dismissed (Got it, tap outside, Back) or satisfied. */
        val dismissAfterMs: Long?,
    ) {
        OVERLAY(
            R.string.launcher_hint_overlay_title,
            R.string.launcher_hint_overlay_description,
            Position.BOTTOM,
            Control.SWITCH,
            3_000L,
        ),
        DEFAULT_HOME(
            R.string.launcher_hint_default_home_title,
            R.string.launcher_hint_default_home_description,
            Position.BOTTOM,
            Control.SHEET,
            3_000L, // closes itself, so the Settings list is free to tap (onboarding and Settings alike)
        ),
        DEFAULT_SMS(
            R.string.launcher_hint_default_sms_title,
            R.string.launcher_hint_default_sms_description,
            Position.TOP,
            Control.NONE,
            2_500L,
        ),
        ;

        fun isSatisfied(context: Context): Boolean = when (this) {
            OVERLAY ->
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

            DEFAULT_HOME -> runCatching {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                context.packageManager.resolveActivity(home, 0)
                    ?.activityInfo?.packageName == context.packageName
            }.getOrDefault(false)

            DEFAULT_SMS -> runCatching {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }.getOrDefault(false)
        }
    }

    enum class Position { TOP, BOTTOM }

    /** SHEET: the design bottom sheet ([R.layout.lx_home_hint_sheet]); the others use the old card layout. */
    enum class Control { SWITCH, NONE, SHEET }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = runCatching { Kind.valueOf(intent?.getStringExtra(EXTRA_KIND).orEmpty()) }
            .getOrElse {
                finish()
                return
            }

        if (kind.control == Control.SHEET) {
            showSheet()
        } else {
            setContentView(R.layout.lx_default_home_hint_scr)
            putType()
        }

        Launcher.events.onScreen("default_home_hint")
        findViewById<View>(R.id.llBaseIx)?.setOnClickListener {
            Launcher.events.onClick("default_home_hint_close")
            finish()
        }
        onBackPressedDispatcher.addCallback(this) {
            Launcher.events.onKey("back")
            finish()
        }
        when (kind.control) {
            Control.SWITCH -> beginSwitcherTween()
            Control.NONE, Control.SHEET -> Unit
        }

        kind.dismissAfterMs?.let { delay ->
            handler.postDelayed({ if (!isFinishing) finish() }, delay)
        }
        handler.post(watchForDone)
    }

    private fun appLabel(): String =
        runCatching { applicationInfo.loadLabel(packageManager).toString() }.getOrElse { packageName }

    private fun showSheet() {
        enableEdgeToEdge()
        setContentView(R.layout.lx_home_hint_sheet)
        val d = resources.displayMetrics.density
        val label = appLabel()

        findViewById<TextView>(R.id.homeHintTitleIx).setText(kind.title)
        findViewById<TextView>(R.id.homeHintAppLabelIx).text = label
        findViewById<TextView>(R.id.homeHintTextIx).text = boldLabel(getString(kind.description, label), label)
        // Variable fonts: neither XML fontVariationSettings nor TextView.fontVariationSettings sticks here; a span does.
        mapOf(R.id.homeHintTitleIx to 700, R.id.homeHintAppLabelIx to 700, R.id.homeHintGotItIx to 600)
            .forEach { (id, w) -> findViewById<TextView>(id)?.let { it.text = weighted(it.text, w) } }
        findViewById<ImageView>(R.id.homeHintAppIconIx).apply {
            setImageDrawable(squareIcon())
            clipToOutline = true
        }
        findViewById<View>(R.id.homeHintTileIx).clipToOutline = true
        findViewById<View>(R.id.homeHintGotItIx).setOnClickListener {
            Launcher.events.onClick("default_home_hint_got_it")
            finish()
        }

        val sheet = findViewById<View>(R.id.homeHintSheetIx)
        val bottomPad = sheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
            v.updatePadding(bottom = bottomPad + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = (BLUR_DP * d).toInt() }
        }

        findViewById<View>(R.id.llBaseIx).apply {
            alpha = 0f
            animate().alpha(1f).setDuration(SCRIM_IN_MS)
        }
        sheet.doOnPreDraw {
            it.translationY = it.height * 1.05f
            it.animate().translationY(0f).setDuration(SHEET_IN_MS).setInterpolator(PathInterpolator(.2f, 0f, 0f, 1f))
        }

        switchAnimator = ObjectAnimator.ofFloat(findViewById(R.id.homeHintSpinIx), View.ROTATION, 0f, 360f).apply {
            duration = SPIN_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        haloAnimator = ObjectAnimator.ofPropertyValuesHolder(
            findViewById(R.id.homeHintHaloIx),
            PropertyValuesHolder.ofFloat(View.SCALE_X, .85f, 1.3f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, .85f, 1.3f),
            PropertyValuesHolder.ofFloat(View.ALPHA, .55f, 0f),
        ).apply {
            duration = HALO_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = PathInterpolator(0f, 0f, .58f, 1f)
        }
        handler.postDelayed(tapLoop, TAP_STEP_MS)
    }

    /** Design's 3-beat loop: hand hovers off the row, presses it (dot fills, halo ripples), lifts. */
    private val tapLoop = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            tapStep = (tapStep + 1) % 3
            val d = resources.displayMetrics.density
            val dot = if (tapStep == 0) 0f else 1f
            val press = if (tapStep == 1) .86f else 1f
            findViewById<View>(R.id.homeHintDotIx).animate().scaleX(dot).scaleY(dot)
                .setDuration(DOT_MS).setInterpolator(PathInterpolator(.3f, 1.6f, .5f, 1f))
            findViewById<View>(R.id.homeHintPointerIx).animate()
                .translationX(if (tapStep == 0) 10 * d else 0f)
                .translationY(if (tapStep == 0) 14 * d else 0f)
                .scaleX(press).scaleY(press)
                .setDuration(POINTER_MS).setInterpolator(PathInterpolator(.3f, 1.4f, .5f, 1f))
            if (tapStep == 1) {
                haloAnimator?.start()
            } else {
                haloAnimator?.cancel()
                findViewById<View>(R.id.homeHintHaloIx).alpha = 0f
            }
            handler.postDelayed(this, TAP_STEP_MS)
        }
    }

    /** The launcher icon as a full-bleed square (adaptive layers unmasked) so the 12dp outline rounds it like the design. */
    /**
     * Our icon as a full square. An adaptive icon's layers are 108dp, but launchers only show the middle 72dp (masks crop
     * there), so draw both layers and keep just that area; drawing the whole 108dp shows the extra margin around the art.
     */
    private fun squareIcon(): Drawable? = runCatching {
        val icon = applicationInfo.loadIcon(packageManager)
        val adaptive = icon as? AdaptiveIconDrawable ?: return@runCatching icon
        val size = (52 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val layers = LayerDrawable(listOfNotNull(adaptive.background, adaptive.foreground).toTypedArray())
        val margin = size / 4 // 108dp layer drawn at 1.5× so its middle 72dp fills the square
        layers.setBounds(-margin, -margin, size + margin, size + margin)
        layers.draw(Canvas(bitmap))
        BitmapDrawable(resources, bitmap)
    }.getOrNull()

    /** [text] drawn at variable-font weight [w] (design `font-weight`). */
    private fun weighted(text: CharSequence, w: Int): CharSequence = SpannableString(text).apply {
        setSpan(object : MetricAffectingSpan() {
            override fun updateDrawState(tp: TextPaint) = updateMeasureState(tp)
            override fun updateMeasureState(tp: TextPaint) { tp.fontVariationSettings = "'wght' $w" }
        }, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun boldLabel(text: String, label: String): CharSequence {
        val at = text.indexOf(label)
        if (at < 0) return text
        return SpannableString(text).apply {
            val end = at + label.length
            setSpan(ForegroundColorSpan(getColor(R.color.lnch_home_hint_ink)), at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            // Variable font: pick the 700 weight instead of StyleSpan's synthetic bold.
            setSpan(object : MetricAffectingSpan() {
                override fun updateDrawState(tp: TextPaint) = updateMeasureState(tp)
                override fun updateMeasureState(tp: TextPaint) {
                    tp.fontVariationSettings = "'wght' 700"
                }
            }, at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun putType() {
        val label = appLabel()
        val icon = runCatching { applicationInfo.loadIcon(packageManager) }.getOrNull()
        listOf(R.id.guideAppIconIx, R.id.guideFakeSheetIconIx).forEach {
            findViewById<android.widget.ImageView>(it)?.setImageDrawable(icon)
        }
        findViewById<android.widget.TextView>(R.id.guideTitleIx)?.text = getString(kind.title, label)
        findViewById<android.widget.TextView>(R.id.guideDescriptionIx)?.setText(kind.description)
        findViewById<android.widget.TextView>(R.id.guideFakeSheetTextIx)?.text = label

        findViewById<View>(R.id.guideSheetFakeIx)?.visibility =
            if (kind.control == Control.SWITCH) View.VISIBLE else View.GONE
        findViewById<View>(R.id.guideItemIx)?.visibility =
            if (kind.control == Control.NONE) View.VISIBLE else View.GONE

        if (kind.position == Position.TOP) {
            findViewById<View>(R.id.guideSpacerIx)?.visibility = View.GONE
            window.setGravity(Gravity.TOP)
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )

            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    private val watchForDone = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (kind.isSatisfied(this@MainLobbyHintStage)) {
                finish()
                return
            }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    private fun beginSwitcherTween() {
        val track = findViewById<FrameLayout>(R.id.guideSwitchRailIx) ?: return
        val thumb = findViewById<View>(R.id.guideSwitchKnobIx) ?: return
        val row = findViewById<View>(R.id.guideFakeSheetItemIx)

        val travel =
            (TRACK_WIDTH_DP - THUMB_SIZE_DP - THUMB_INSET_DP * 2) * resources.displayMetrics.density

        val slide = ObjectAnimator.ofFloat(thumb, View.TRANSLATION_X, 0f, travel).apply {
            duration = FLIP_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val tint = ValueAnimator.ofObject(ArgbEvaluator(), getColor(R.color.lnch_hint_switch_off), getColor(R.color.lnch_hint_accent)).apply {
            duration = FLIP_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                track.backgroundTintList = ColorStateList.valueOf(animator.animatedValue as Int)
            }
        }

        switchAnimator = AnimatorSet().apply {
            startDelay = HOLD_MS
            playTogether(listOf(slide, tint) + lift(row))
            start()
        }
    }

    private fun lift(row: View?): List<Animator> = row?.let {
        listOf(
            ObjectAnimator.ofFloat(it, View.SCALE_X, 1f, ROW_LIFT_SCALE),
            ObjectAnimator.ofFloat(it, View.SCALE_Y, 1f, ROW_LIFT_SCALE),
            ObjectAnimator.ofFloat(it, View.TRANSLATION_Y, 0f, -ROW_LIFT_DP * resources.displayMetrics.density),
        ).map { animator ->
            animator.apply {
                duration = FLIP_MS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
        }
    }.orEmpty()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        switchAnimator?.cancel()
        switchAnimator = null
        haloAnimator?.cancel()
        haloAnimator = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OverlayFlow"
        private const val EXTRA_KIND = "hint_kind"

        fun surfaceForLayer(activity: Activity) = show(activity, Kind.OVERLAY)

        fun surfaceForMainAtrium(activity: Activity) = show(activity, Kind.DEFAULT_HOME)

        fun show(activity: Activity) = show(activity, Kind.DEFAULT_HOME)

        fun surfaceForMainText(activity: Activity) = show(activity, Kind.DEFAULT_SMS)

        fun show(activity: Activity, kind: Kind) {
            if (kind.isSatisfied(activity)) return

            val handler = Handler(Looper.getMainLooper())
            val startedAt = SystemClock.elapsedRealtime()
            handler.postDelayed(object : Runnable {
                override fun run() {
                    if (activity.isFinishing || activity.isDestroyed) return
                    if (kind.isSatisfied(activity)) return

                    val waited = SystemClock.elapsedRealtime() - startedAt
                    if (activity.hasWindowFocus() && waited < GIVE_UP_MS) {
                        handler.postDelayed(this, POLL_MS)
                        return
                    }
                    if (activity.hasWindowFocus()) {
                        Timber.tag(TAG).w("%s screen never came up — no hint", kind)
                        return
                    }
                    handler.postDelayed({ launch(activity, kind) }, SETTLE_MS)
                }
            }, FIRST_CHECK_MS)
        }

        private fun launch(activity: Activity, kind: Kind) {
            if (activity.isFinishing || activity.isDestroyed) return
            if (kind.isSatisfied(activity)) return
            runCatching {
                activity.startActivity(
                    Intent(activity, MainLobbyHintStage::class.java)
                        .putExtra(EXTRA_KIND, kind.name)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
                Timber.tag(TAG).d("hint shown: %s", kind)
            }.onFailure { Timber.tag(TAG).w(it, "could not show the %s hint", kind) }
        }

        private const val FIRST_CHECK_MS = 250L
        private const val POLL_MS = 150L

        private const val SETTLE_MS = 350L

        private const val GIVE_UP_MS = 6_000L

        private const val CHECK_MS = 300L

        private const val FLIP_MS = 700L
        private const val HOLD_MS = 250L

        private const val TRACK_WIDTH_DP = 52f
        private const val THUMB_SIZE_DP = 26f
        private const val THUMB_INSET_DP = 3f

        private const val ROW_LIFT_SCALE = 1.04f
        private const val ROW_LIFT_DP = 3f

        private const val BLUR_DP = 3f
        private const val SCRIM_IN_MS = 300L
        private const val SHEET_IN_MS = 380L
        private const val SPIN_MS = 2_400L
        private const val HALO_MS = 1_200L
        private const val TAP_STEP_MS = 2_600L
        private const val DOT_MS = 250L
        private const val POINTER_MS = 450L
    }
}
