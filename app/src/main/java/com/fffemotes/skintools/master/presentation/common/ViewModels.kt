package com.fffemotes.skintools.master.presentation.common

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fffemotes.skintools.master.App
import com.fffemotes.skintools.master.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * `by appViewModel { handle -> MyViewModel(repo, handle) }`: builds the screen's ViewModel from the [AppContainer].
 * The SavedStateHandle carries the activity's intent extras and survives recreation (e.g. a language change).
 */
inline fun <reified VM : ViewModel> ComponentActivity.appViewModel(
    crossinline create: AppContainer.(SavedStateHandle) -> VM,
): Lazy<VM> = viewModels {
    viewModelFactory {
        initializer { (this@appViewModel.application as App).container.create(createSavedStateHandle()) }
    }
}

/** Collects [flow] while the screen is at least started. */
fun <T> LifecycleOwner.collectWhenStarted(flow: Flow<T>, action: (T) -> Unit) {
    lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { action(it) } } }
}
