package com.posprint.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.IncomingShare
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.SettingsStore
import com.posprint.app.data.TemplateRepository
import com.posprint.app.data.settings.UiPrefs
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val printers: PrinterRepository,
    private val templates: TemplateRepository,
    val share: IncomingShare,
) : ViewModel() {

    val ui: StateFlow<UiPrefs> = settings.ui.stateIn(viewModelScope, SharingStarted.Eagerly, UiPrefs())

    /** First launch with no printer saved goes through onboarding; everyone else lands on Home. */
    suspend fun resolveStartRoute(): String {
        val done = settings.onboardingDone.first()
        val hasPrinter = printers.observeAll().first().isNotEmpty()
        return if (done || hasPrinter) "home" else "onboarding"
    }

    suspend fun newLabel(): Template = templates.createBlank(TemplateKind.LABEL)
}
