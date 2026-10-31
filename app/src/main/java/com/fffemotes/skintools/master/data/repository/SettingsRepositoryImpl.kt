package com.fffemotes.skintools.master.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fffemotes.skintools.master.data.local.PreferencesDataSource
import com.fffemotes.skintools.master.data.local.SUPPORTED_LANGUAGES
import com.fffemotes.skintools.master.domain.model.Language
import com.fffemotes.skintools.master.domain.repository.SettingsRepository
import java.util.Locale

/** The onboarding flag lives in preferences; the language is AppCompat's per-app locale, which it persists itself. */
class SettingsRepositoryImpl(private val prefs: PreferencesDataSource) : SettingsRepository {
    override val languages: List<Language> = SUPPORTED_LANGUAGES

    override fun currentLanguageIndex(): Int {
        val tag = (AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()).language
        // Android reports Indonesian with the legacy code "in".
        val norm = if (tag == "in") "id" else tag
        return languages.indexOfFirst { it.tag == norm }.coerceAtLeast(0)
    }

    override fun setLanguage(index: Int) {
        if (index != currentLanguageIndex()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languages[index].tag))
        }
    }

    override fun isOnboarded(): Boolean = prefs.onboarded

    override fun answer(step: String): Set<Int>? = prefs.answer(step)

    override fun setAnswer(step: String, selection: Set<Int>) = prefs.saveAnswers(mapOf(step to selection))

    override fun takeFirstHome(): Boolean = prefs.firstHomePending.also { if (it) prefs.firstHomePending = false }

    override fun setOnboarded(answers: Map<String, Set<Int>>) {
        prefs.saveAnswers(answers)
        prefs.firstHomePending = true
        prefs.onboarded = true
    }
}
