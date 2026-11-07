package com.posprint.app.data.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.posprint.app.domain.model.PrintError
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.printer.PrinterTransport
import com.posprint.app.util.hasBluetoothConnect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Classic Bluetooth (RFCOMM / SPP), what practically all thermal receipt printers use. */
@SuppressLint("MissingPermission") // checked in open()
class BluetoothTransport(
    private val context: Context,
    private val address: String,
) : PrinterTransport {

    @Volatile private var socket: BluetoothSocket? = null

    override val isOpen: Boolean get() = socket?.isConnected == true

    override suspend fun open() = withContext(Dispatchers.IO) {
        if (!context.hasBluetoothConnect()) throw PrinterException(PrintError.BluetoothPermissionDenied)
        val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) throw PrinterException(PrintError.BluetoothOff)
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw PrinterException(PrintError.DeviceNotFound, e)
        }
        adapter.cancelDiscovery() // discovery slows and destabilises connect()
        close()
        socket = try {
            connect(device, secure = true)
        } catch (first: IOException) {
            // Some printers only accept the insecure channel.
            try {
                connect(device, secure = false)
            } catch (second: IOException) {
                throw PrinterException(PrintError.ConnectionFailed(second.message), second)
            }
        } catch (e: SecurityException) {
            throw PrinterException(PrintError.BluetoothPermissionDenied, e)
        }
    }

    private fun connect(device: BluetoothDevice, secure: Boolean): BluetoothSocket {
        val s = if (secure) device.createRfcommSocketToServiceRecord(SPP)
        else device.createInsecureRfcommSocketToServiceRecord(SPP)
        try {
            s.connect()
        } catch (e: IOException) {
            runCatching { s.close() }
            throw e
        }
        return s
    }

    override suspend fun write(data: ByteArray) {
        val out = socket?.outputStream ?: throw IOException("Not connected")
        withContext(Dispatchers.IO) {
            var offset = 0
            while (offset < data.size) {
                val n = minOf(CHUNK, data.size - offset)
                out.write(data, offset, n)
                out.flush()
                offset += n
                // Cheap printers have tiny buffers; pacing avoids garbled output on big jobs.
                if (offset < data.size) delay(PACE_MS)
            }
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val input = socket?.inputStream ?: throw IOException("Not connected")
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (input.available() == 0 && System.nanoTime() < deadline) delay(10)
        val n = minOf(input.available(), maxBytes)
        if (n == 0) ByteArray(0) else ByteArray(n).also { input.read(it, 0, n) }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CHUNK = 512
        const val PACE_MS = 10L
    }
}
