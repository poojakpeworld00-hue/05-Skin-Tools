package com.fffemotes.skintools.master.presentation.catalog

import com.fffemotes.skintools.master.domain.model.CatalogType

/** How a tile frames its image: [ratio] is width:height; [fullBleed] crops edge to edge instead of fitting with padding. */
enum class TileShape(val ratio: String, val fullBleed: Boolean) {
    SQUARE("1:1", false),
    MAP_COVER("309:592", true), // the six map cover cards
    BANNER("1460:500", true), // map location artwork, which has its name printed on it
}

val CatalogType.groupShape: TileShape get() = if (this == CatalogType.MAPS) TileShape.MAP_COVER else TileShape.SQUARE
val CatalogType.itemShape: TileShape get() = if (this == CatalogType.MAPS) TileShape.BANNER else TileShape.SQUARE
