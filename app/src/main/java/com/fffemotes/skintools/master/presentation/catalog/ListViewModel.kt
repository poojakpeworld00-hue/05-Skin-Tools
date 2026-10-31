package com.fffemotes.skintools.master.presentation.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.fffemotes.skintools.master.domain.model.CatalogType
import com.fffemotes.skintools.master.domain.repository.CatalogRepository
import com.fffemotes.skintools.master.presentation.common.UiText

/** A section's categories (weapon types, maps, emote rarities) or one category's items, from the screen's arguments. */
class ListViewModel(repository: CatalogRepository, args: SavedStateHandle) : ViewModel() {
    val type: CatalogType = CatalogType.entries[args[ListActivity.EXTRA_CATALOG] ?: 0]
    private val section = repository.section(type)
    private val groupArg: Int = args[ListActivity.EXTRA_GROUP] ?: -1

    /** True when this screen lists categories rather than items. */
    val showsGroups = section.groups.size > 1 && groupArg < 0

    /** The section has categories (emotes, weapons, maps), whichever level this screen shows. */
    val hasGroups = section.groups.size > 1
    val groupIndex = groupArg.coerceAtLeast(0)

    val title: UiText = if (showsGroups) UiText.Res(section.title)
    else section.groups[groupIndex].name?.let { UiText.Plain(it) } ?: UiText.Res(section.title)

    val tiles: List<Tile> = if (showsGroups) section.groups.map { Tile(it.name, it.image) }
    else section.groups[groupIndex].items.map { Tile(it.name, it.thumb) }

    val columns = if (showsGroups) 2 else type.columns
    val shape = if (showsGroups) type.groupShape else type.itemShape
}
