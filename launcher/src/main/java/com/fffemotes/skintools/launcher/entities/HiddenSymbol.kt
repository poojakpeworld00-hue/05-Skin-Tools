package com.fffemotes.skintools.launcher.entities

import android.graphics.drawable.Drawable
import androidx.room.*

@Entity(tableName = "hidden_icons", indices = [(Index(value = ["id"], unique = true))])
data class HiddenSymbol(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "package_name") var packageName: String,
    @ColumnInfo(name = "activity_name") var activityName: String,
    @ColumnInfo(name = "title") var title: String,

    @Ignore var drawable: Drawable? = null,
) {
    constructor() : this(null, "", "", "", null)

    fun pullGlyphIdentifier() = "$packageName/$activityName"
}
