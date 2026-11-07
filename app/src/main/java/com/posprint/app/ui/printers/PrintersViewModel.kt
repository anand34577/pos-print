package com.posprint.app.ui.printers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.printer.PrintService
import com.posprint.app.data.printer.PrinterConnectionManager
import com.posprint.app.domain.model.ConnectionState
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrintersUiState(
    val printers: List<Printer> = emptyList(),
    val connections: Map<Long, ConnectionState> = emptyMap(),
    val jobs: List<PrintJob> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class PrintersViewModel @Inject constructor(
    private val repo: PrinterRepository,
    private val connections: PrinterConnectionManager,
    private val print: PrintService,
) : ViewModel() {

    val state: StateFlow<PrintersUiState> = combine(
        repo.observeAll(), connections.states, print.jobs,
    ) { printers, states, jobs -> PrintersUiState(printers, states, jobs, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrintersUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun connect(printer: Printer) {
        viewModelScope.launch {
            try {
                connections.connect(printer)
                val status = print.checkStatus(printer)
                _messages.send(
                    status?.toError()?.message
                        ?: if (status?.paperNearEnd == true) "Connected. Paper is running low."
                        else "Connected to ${printer.name}.",
                )
            } catch (e: PrinterException) {
                _messages.send(e.error.message)
            }
        }
    }

    fun disconnect(printer: Printer) = connections.disconnect(printer.id)
    fun testPrint(printer: Printer) { print.enqueueTestPage(printer) }
    fun selfTest(printer: Printer) {
        if (print.enqueueSelfTest(printer) == null) _messages.trySend("This printer language has no built-in self-test. Use the printer's feed button instead.")
    }

    fun update(printer: Printer) {
        viewModelScope.launch {
            connections.disconnect(printer.id) // profile change may alter how we talk to it
            repo.update(printer)
        }
    }
    fun retry(id: Long) = print.retry(id)
    fun cancel(id: Long) = print.cancel(id)
    fun setDefault(printer: Printer) { viewModelScope.launch { repo.setDefault(printer.id) } }

    fun delete(printer: Printer) {
        viewModelScope.launch {
            connections.disconnect(printer.id)
            repo.delete(printer.id)
        }
    }
}
