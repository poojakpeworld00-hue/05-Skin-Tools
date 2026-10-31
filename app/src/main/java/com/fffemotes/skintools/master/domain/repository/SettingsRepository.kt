package com.fffemotes.skintools.master.domain.repository

import com.fffemotes.skintools.master.domain.model.Language

interface SettingsRepository {
    val languages: List<Language>

    /** Index into [languages] of the app language (per-app locale, else the device locale). */
    fun currentLanguageIndex(): Int

    /** Switches the app language; running screens are recreated in the new language. */
    fun setLanguage(index: Int)

    fun isOnboarded(): Boolean

    /** Marks onboarding done and saves its answers: step key → selected option indices. */
    fun setOnboarded(answers: Map<String, Set<Int>>)

    /** A saved onboarding answer (option indices), or null when the step was never answered. */
    fun answer(step: String): Set<Int>?

    fun setAnswer(step: String, selection: Set<Int>)

    /** True once, on the first Home visit after onboarding (`first_home` analytics). */
    fun takeFirstHome(): Boolean
}
