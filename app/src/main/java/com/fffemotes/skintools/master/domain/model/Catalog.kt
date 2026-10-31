package com.fffemotes.skintools.master.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** One skin/character/location. Content text is English, as in the reference app. */
data class Item(
    val name: String,
    @DrawableRes val image: Int,
    val desc: String,
    val stats: List<Pair<Int, String>> = emptyList(),
    @DrawableRes val thumb: Int = image,
    val title: String = name,
)

/** A sub-category (weapon type, map, emote rarity); a null name means the section has no categories. */
data class Group(val name: String?, @DrawableRes val image: Int, val items: List<Item>)

data class Section(@StringRes val title: Int, val groups: List<Group>)

data class Article(val title: String, val summary: String, val body: String)

/** The browsable content sections. [columns] is the item grid width. */
enum class CatalogType(val columns: Int) {
    CHARACTERS(3), BUNDLES(3), EMOTES(3), WEAPONS(3), PETS(3), VEHICLES(2), MAPS(1),
}
