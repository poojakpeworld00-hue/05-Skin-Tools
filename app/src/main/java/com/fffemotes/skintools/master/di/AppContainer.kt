package com.fffemotes.skintools.master.di

import android.content.Context
import com.fffemotes.skintools.master.data.local.PreferencesDataSource
import com.fffemotes.skintools.master.data.repository.CatalogRepositoryImpl
import com.fffemotes.skintools.master.data.repository.MediaRepositoryImpl
import com.fffemotes.skintools.master.data.repository.SettingsRepositoryImpl
import com.fffemotes.skintools.master.domain.repository.CatalogRepository
import com.fffemotes.skintools.master.domain.repository.MediaRepository
import com.fffemotes.skintools.master.domain.repository.SettingsRepository
import com.fffemotes.skintools.master.domain.usecase.CalculateUseCase
import com.fffemotes.skintools.master.data.remote.config.AppConfig
import com.fffemotes.skintools.master.data.remote.config.AppConfigStore
import kotlinx.coroutines.flow.StateFlow

/** Manual dependency wiring: one instance per app, created in [com.fffemotes.skintools.master.App]. */
class AppContainer(context: Context) {
    private val preferences = PreferencesDataSource(context)

    val catalogRepository: CatalogRepository = CatalogRepositoryImpl()
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(preferences)
    val mediaRepository: MediaRepository = MediaRepositoryImpl(context.applicationContext)
    val calculate = CalculateUseCase()

    /** The live Remote Config ([AppConfigStore] is started in App.onCreate). */
    val config: StateFlow<AppConfig> = AppConfigStore.current
}
