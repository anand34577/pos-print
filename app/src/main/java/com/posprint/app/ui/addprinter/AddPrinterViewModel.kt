package com.posprint.app.ui.addprinter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.SettingsStore
import com.posprint.app.data.discovery.BluetoothDiscovery
import com.posprint.app.data.discovery.UsbDiscovery
import com.posprint.app.data.discovery.sortedForDisplay
import com.posprint.app.data.printer.PrintService
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.DiscoveredDevice
import com.posprint.app.domain.model.PaperWidth
import com.posprint.app.domain.model.PrintError
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.domain.model.Printer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WizardStep { TYPE, DEVICE, TEST }

data class AddPrinterUiState(
    val step: WizardStep = WizardStep.TYPE,
    val type: ConnectionType? = null,
    val devices: List<DiscoveredDevice> = emptyList(),
    val scanning: Boolean = false,
    val busyAddress: String? = null,
    val error: String? = null,
    val needsBluetoothEnable: Boolean = false,
    val printer: Printer? = null,
    val testJob: PrintJob? = null,
)

@HiltViewModel
class AddPrinterViewModel @Inject constructor(
    private val bluetooth: BluetoothDiscovery,
    private val usb: UsbDiscovery,
    private val repo: PrinterRepository,
    private val print: PrintService,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(AddPrinterUiState())
    val ui: StateFlow<AddPrinterUiState> = _ui.asStateFlow()

    private var scanJob: Job? = null
    private var testJobId: Long? = null

    init {
        viewModelScope.launch {
            print.jobs.collect { jobs ->
                val id = testJobId
                _ui.update { it.copy(testJob = jobs.firstOrNull { j -> j.id == id }) }
            }
        }
    }

    fun chooseType(type: ConnectionType) {
        _ui.update { it.copy(type = type, step = WizardStep.DEVICE, devices = emptyList(), error = null) }
        startScan()
    }

    fun showError(message: String) = _ui.update { it.copy(error = message) }

    fun startScan() {
        val type = _ui.value.type ?: return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _ui.update { it.copy(scanning = true, error = null, needsBluetoothEnable = false) }
            try {
                when (type) {
                    ConnectionType.BLUETOOTH -> {
                        if (!bluetooth.isEnabled()) {
                            _ui.update { it.copy(needsBluetoothEnable = true) }
                            return@launch
                        }
                        _ui.update { it.copy(devices = bluetooth.bonded()) }
                        bluetooth.scan().collect { found ->
                            _ui.update { s ->
                                s.copy(devices = (s.devices.filterNot { it.address == found.address } + found).sortedForDisplay())
                            }
                        }
                    }
                    ConnectionType.USB -> usb.observe().collect { list ->
                        _ui.update { it.copy(devices = list, scanning = false) }
                    }
                }
            } catch (e: SecurityException) {
                _ui.update { it.copy(error = PrintError.BluetoothPermissionDenied.message) }
            } finally {
                _ui.update { it.copy(scanning = false) }
            }
        }
    }

    fun pick(device: DiscoveredDevice) {
        viewModelScope.launch {
            _ui.update { it.copy(busyAddress = device.address, error = null) }
            try {
                if (device.connection == ConnectionType.BLUETOOTH && !device.paired && !bluetooth.pair(device.address)) {
                    _ui.update { it.copy(error = "Pairing failed. Switch the printer on, keep it close, and enter its PIN if asked (often 0000 or 1234).") }
                    return@launch
                }
                val printer = repo.findByAddress(device.address) ?: repo.add(
                    Printer(name = device.name, connection = device.connection, address = device.address),
                )
                scanJob?.cancel()
                testJobId = null
                _ui.update { it.copy(printer = printer, step = WizardStep.TEST, testJob = null) }
            } catch (e: SecurityException) {
                _ui.update { it.copy(error = PrintError.BluetoothPermissionDenied.message) }
            } finally {
                _ui.update { it.copy(busyAddress = null) }
            }
        }
    }

    fun setPaper(width: PaperWidth) = editPrinter { it.copy(paperWidth = width) }
    fun setCutter(has: Boolean) = editPrinter { it.copy(hasCutter = has) }
    fun setLanguage(l: com.posprint.app.domain.model.PrinterLanguage) = editPrinter { it.copy(language = l) }

    private fun editPrinter(change: (Printer) -> Printer) {
        val current = _ui.value.printer ?: return
        val updated = change(current)
        _ui.update { it.copy(printer = updated) }
        viewModelScope.launch { repo.update(updated) }
    }

    fun testPrint() {
        val printer = _ui.value.printer ?: return
        testJobId = print.enqueueTestPage(printer)
    }

    fun retryTest() { testJobId?.let(print::retry) }

    fun back(): Boolean = when (_ui.value.step) {
        WizardStep.TYPE -> false
        WizardStep.DEVICE -> {
            scanJob?.cancel()
            _ui.update { it.copy(step = WizardStep.TYPE, scanning = false, error = null) }
            true
        }
        WizardStep.TEST -> {
            _ui.update { it.copy(step = WizardStep.DEVICE) }
            startScan()
            true
        }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone()
            onDone()
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
