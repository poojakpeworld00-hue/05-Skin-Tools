package com.fffemotes.skintools.master.presentation.articles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.domain.model.Article
import com.fffemotes.skintools.master.domain.repository.CatalogRepository

/** Tips & Tricks or Diamond Tips (by the "guides" argument), for the list and the article screens. */
class ArticlesViewModel(repository: CatalogRepository, args: SavedStateHandle) : ViewModel() {
    val guides: Boolean = args[ArticlesActivity.EXTRA_GUIDES] ?: false
    val articles: List<Article> = if (guides) repository.diamondGuides() else repository.tips()
    val title = if (guides) R.string.tool_diamond else R.string.tool_tips
    val icon = if (guides) R.drawable.ic_diamond else R.drawable.ic_bulb

    /** The article opened on the article screen. */
    val index: Int = args[ArticleActivity.EXTRA_INDEX] ?: 0
    val article: Article get() = articles[index]

    /** Numbered tips show "Tip n"; diamond-tip guides are unnumbered, as in the reference app. */
    val showsNumber get() = !guides
}
