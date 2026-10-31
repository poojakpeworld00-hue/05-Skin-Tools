package com.fffemotes.skintools.master.domain.repository

import androidx.annotation.DrawableRes

interface MediaRepository {
    /** Saves the image to the gallery (Pictures/SkinEmotes) as "<title>.png"; false if it failed. */
    fun saveToGallery(@DrawableRes image: Int, title: String): Boolean
}
