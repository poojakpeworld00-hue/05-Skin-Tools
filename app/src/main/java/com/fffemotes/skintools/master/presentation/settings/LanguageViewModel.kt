package com.fffemotes.skintools.master.presentation.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.fffemotes.skintools.master.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class LanguageViewModel(private val settings: SettingsRepository, private val handle: SavedStateHandle) : ViewModel() {
    val languages = settings.languages

    /** The highlighted (not yet applied) language. */
    val selected: StateFlow<Int> = handle.getStateFlow(KEY_SELECTED, settings.currentLanguageIndex())

    fun select(index: Int) {
        handle[KEY_SELECTED] = index
    }

    /** Switches the app language; screens behind this one are recreated in it. */
    fun apply() = settings.setLanguage(selected.value)

    private companion object {
        const val KEY_SELECTED = "selected"
    }
}
