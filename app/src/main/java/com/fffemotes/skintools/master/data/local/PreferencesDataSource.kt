package com.fffemotes.skintools.master.data.local

import android.content.Context
import androidx.core.content.edit

/** Key-value storage for user choices. The file name is kept from earlier builds so saved choices survive. */
class PreferencesDataSource(context: Context) {
    private val sp = context.getSharedPreferences("skinhub", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) = sp.edit { putBoolean("onboarded", value) }

    var firstHomePending: Boolean
        get() = sp.getBoolean("first_home_pending", false)
        set(value) = sp.edit { putBoolean("first_home_pending", value) }

    fun answer(step: String): Set<Int>? =
        sp.getString("answer_$step", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet()

    /** Onboarding answers as `answer_<step key>` = comma-separated option indices. */
    fun saveAnswers(answers: Map<String, Set<Int>>) = sp.edit {
        answers.forEach { (step, selection) -> putString("answer_$step", selection.sorted().joinToString(",")) }
    }
}
