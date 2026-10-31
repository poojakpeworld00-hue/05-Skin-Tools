package com.fffemotes.skintools.master.domain.repository

import com.fffemotes.skintools.master.domain.model.Article
import com.fffemotes.skintools.master.domain.model.CatalogType
import com.fffemotes.skintools.master.domain.model.Section

interface CatalogRepository {
    fun section(type: CatalogType): Section
    fun tips(): List<Article>
    fun diamondGuides(): List<Article>
}
