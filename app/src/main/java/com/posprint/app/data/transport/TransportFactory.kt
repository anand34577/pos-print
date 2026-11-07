package com.posprint.app.data.transport

import android.content.Context
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.printer.PrinterTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransportFactory @Inject constructor(@ApplicationContext private val context: Context) {
    fun create(printer: Printer): PrinterTransport = when (printer.connection) {
        ConnectionType.BLUETOOTH -> BluetoothTransport(context, printer.address)
        ConnectionType.USB -> UsbTransport(context, printer.address)
    }
}
