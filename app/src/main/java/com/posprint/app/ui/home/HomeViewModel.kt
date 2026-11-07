package com.posprint.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.SettingsStore
import com.posprint.app.data.printer.PrintService
import com.posprint.app.data.printer.PrinterConnectionManager
import com.posprint.app.domain.model.ConnectionState
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val printer: Printer? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val jobs: List<PrintJob> = emptyList(),
    val business: String = "",
    val loading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: PrinterRepository,
    settings: SettingsStore,
    private val connections: PrinterConnectionManager,
    private val print: PrintService,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repo.observeDefault(), connections.states, print.jobs, settings.profile.map { it.name },
    ) { printer, states, jobs, business ->
        HomeUiState(
            printer = printer,
            connection = printer?.let { states[it.id] } ?: ConnectionState.DISCONNECTED,
            jobs = jobs.take(5),
            business = business,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun connect(printer: Printer) {
        viewModelScope.launch {
            try { connections.connect(printer) } catch (_: PrinterException) { /* state flow shows ERROR */ }
        }
    }

    fun testPrint(printer: Printer) { print.enqueueTestPage(printer) }
    fun retry(id: Long) = print.retry(id)
    fun cancel(id: Long) = print.cancel(id)
}
