package com.fffemotes.skintools.master.presentation.common

import android.content.Context
import androidx.annotation.StringRes

/** Text a ViewModel can produce without a Context: a string resource or content text. */
sealed interface UiText {
    data class Res(@StringRes val id: Int) : UiText
    data class Plain(val text: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id)
        is Plain -> text
    }
}
