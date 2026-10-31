package com.fffemotes.skintools.master.presentation.articles

import com.fffemotes.skintools.master.presentation.common.bindScreenAd
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.ads.analytics.Analytics
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.fffemotes.skintools.master.presentation.common.enableDarkEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.databinding.ActivityArticleBinding
import com.fffemotes.skintools.master.databinding.ActivityArticlesBinding
import com.fffemotes.skintools.master.databinding.ItemToolBinding
import com.fffemotes.skintools.master.presentation.common.appViewModel
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.common.listAd
import com.fffemotes.skintools.master.presentation.common.insertInto

/** Tips & Tricks and Diamond Tips: a list of articles. */
class ArticlesActivity : AppCompatActivity() {
    private val viewModel by appViewModel { handle -> ArticlesViewModel(catalogRepository, handle) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        val b = ActivityArticlesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.header.back.setOnClickListener { finish() }

        b.header.title.setText(viewModel.title)
        viewModel.articles.forEachIndexed { i, a ->
            val row = ItemToolBinding.inflate(layoutInflater, b.rows, true)
            row.icon.setImageResource(viewModel.icon)
            row.name.text = a.title
            row.sub.text = a.summary
            row.sub.visibility = if (a.summary.isEmpty()) View.GONE else View.VISIBLE
            row.root.setOnClickListener {
                Analytics.click("article", i)
                startActivity(ArticleActivity.intent(this, viewModel.guides, i))
            }
        }
        listAd(if (viewModel.guides) "screens.diamond_tips" else "screens.tips")
            ?.insertInto(b.rows, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._7sdp))
        bindScreenAd("screens.articles", b.adInline, b.adSlot)
    }

    override fun onResume() {
        super.onResume()
        Screens.show("articles")
    }

    companion object {
        const val EXTRA_GUIDES = "guides"

        fun intent(context: Context, guides: Boolean): Intent =
            Intent(context, ArticlesActivity::class.java).putExtra(EXTRA_GUIDES, guides)
    }
}

class ArticleActivity : AppCompatActivity() {
    private val viewModel by appViewModel { handle -> ArticlesViewModel(catalogRepository, handle) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        val b = ActivityArticleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.header.back.setOnClickListener { finish() }

        val a = viewModel.article
        b.header.title.setText(viewModel.title)
        b.number.visibility = if (viewModel.showsNumber) View.VISIBLE else View.GONE
        b.number.text = getString(R.string.tip_number, viewModel.index + 1)
        b.title.text = a.title
        b.body.text = a.body
        // Diamond tips: its own place, the bottom ad pinned under the text; tips keep screens.article.
        if (viewModel.guides) Ads.bottom(this, b.adSlot, "screens.diamond_article")
        else bindScreenAd("screens.article", b.adInline, b.adSlot)
    }

    override fun onResume() {
        super.onResume()
        Screens.show("article")
    }

    companion object {
        const val EXTRA_INDEX = "index"

        fun intent(context: Context, guides: Boolean, index: Int): Intent =
            Intent(context, ArticleActivity::class.java)
                .putExtra(ArticlesActivity.EXTRA_GUIDES, guides)
                .putExtra(EXTRA_INDEX, index)
    }
}
