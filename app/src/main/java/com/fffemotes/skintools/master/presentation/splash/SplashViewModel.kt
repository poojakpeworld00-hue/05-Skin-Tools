package com.fffemotes.skintools.master.presentation.splash

import androidx.lifecycle.ViewModel
import com.fffemotes.skintools.master.domain.repository.SettingsRepository

class SplashViewModel(private val settings: SettingsRepository) : ViewModel() {
    enum class Destination { ONBOARDING, HOME }

    fun destination(): Destination = if (settings.isOnboarded()) Destination.HOME else Destination.ONBOARDING
}
