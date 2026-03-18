package com.openclaw.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.agent.data.preferences.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore
) : ViewModel() {

    val selectedModel: Flow<String> = settingsStore.selectedModelFlow
    val availableModels = SettingsStore.AVAILABLE_MODELS

    private val _apiKey = MutableStateFlow(settingsStore.getApiKey())
    val apiKey: StateFlow<String> = _apiKey

    fun saveApiKey(key: String) {
        settingsStore.saveApiKey(key)
        _apiKey.value = key
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settingsStore.saveSelectedModel(modelId)
        }
    }
}
