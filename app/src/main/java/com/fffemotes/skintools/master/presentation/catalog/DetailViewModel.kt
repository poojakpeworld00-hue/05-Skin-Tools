package com.fffemotes.skintools.master.presentation.catalog

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.domain.model.CatalogType
import com.fffemotes.skintools.master.domain.model.Item
import com.fffemotes.skintools.master.domain.repository.CatalogRepository
import com.fffemotes.skintools.master.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel(
    repository: CatalogRepository,
    private val media: MediaRepository,
    args: SavedStateHandle,
) : ViewModel() {
    val type: CatalogType = CatalogType.entries[args[DetailActivity.EXTRA_CATALOG] ?: 0]
    val item: Item = repository.section(type)
        .groups[args[DetailActivity.EXTRA_GROUP] ?: 0]
        .items[args[DetailActivity.EXTRA_ITEM] ?: 0]

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** One-off messages (string resources) to show as a toast. */
    val messages = _messages.receiveAsFlow()

    /** Saves the item image to the gallery; the caller must hold the storage permission below Android 10. */
    fun download() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { media.saveToGallery(item.image, item.name) }
            _messages.send(message(saved))
        }
    }

    @StringRes
    private fun message(saved: Boolean) = if (saved) R.string.download_done else R.string.download_failed
}
