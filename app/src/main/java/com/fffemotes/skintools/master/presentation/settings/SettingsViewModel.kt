package com.fffemotes.skintools.master.presentation.settings

import androidx.lifecycle.ViewModel
import com.fffemotes.skintools.master.domain.model.Language
import com.fffemotes.skintools.master.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SettingsUiState(val language: Language)

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsUiState> = _state

    /** Re-reads settings, e.g. after returning from the language screen. */
    fun refresh() {
        _state.value = read()
    }

    private fun read() = SettingsUiState(settings.languages[settings.currentLanguageIndex()])
}
