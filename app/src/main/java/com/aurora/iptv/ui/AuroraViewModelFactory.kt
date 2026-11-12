package com.aurora.iptv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aurora.iptv.AppContainer

/** Tiny manual factory; both screen view models need the application container. */
class AuroraViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AppViewModel::class.java) -> AppViewModel(container) as T
        modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(container) as T
        else -> throw IllegalArgumentException("Unknown view model: ${modelClass.name}")
    }
}
