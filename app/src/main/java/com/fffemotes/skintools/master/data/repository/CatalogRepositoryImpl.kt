package com.fffemotes.skintools.master.data.repository

import com.fffemotes.skintools.master.data.local.Content
import com.fffemotes.skintools.master.domain.model.Article
import com.fffemotes.skintools.master.domain.model.CatalogType
import com.fffemotes.skintools.master.domain.model.Section
import com.fffemotes.skintools.master.domain.repository.CatalogRepository

/** Serves the bundled content generated from the reference app (data/local/Content.kt). */
class CatalogRepositoryImpl : CatalogRepository {
    override fun section(type: CatalogType): Section = when (type) {
        CatalogType.CHARACTERS -> Content.CHARACTERS
        CatalogType.BUNDLES -> Content.BUNDLES
        CatalogType.EMOTES -> Content.EMOTES
        CatalogType.WEAPONS -> Content.WEAPONS
        CatalogType.PETS -> Content.PETS
        CatalogType.VEHICLES -> Content.VEHICLES
        CatalogType.MAPS -> Content.MAPS
    }

    override fun tips(): List<Article> = Content.TIPS
    override fun diamondGuides(): List<Article> = Content.DIAMOND_TIPS
}
