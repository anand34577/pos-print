package com.posprint.app.data.transport

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.posprint.app.domain.model.PrintError
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.printer.PrinterTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.coroutines.resume

/** USB host mode, bulk transfer to the printer-class interface. [address] is "vendorId:productId". */
class UsbTransport(
    private val context: Context,
    private val address: String,
) : PrinterTransport {

    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager

    @Volatile private var connection: UsbDeviceConnection? = null
    private var claimed: UsbInterface? = null
    private var outEndpoint: UsbEndpoint? = null
    private var inEndpoint: UsbEndpoint? = null

    override val isOpen: Boolean get() = connection != null &&
        usb.deviceList.values.any { "${it.vendorId}:${it.productId}" == address } // false once unplugged

    override suspend fun open() {
        val device = usb.deviceList.values.firstOrNull { "${it.vendorId}:${it.productId}" == address }
            ?: throw PrinterException(PrintError.DeviceNotFound)
        ensurePermission(device)
        withContext(Dispatchers.IO) {
            close()
            val (intf, out, input) = findEndpoints(device)
                ?: throw PrinterException(PrintError.NoUsbEndpoint)
            val conn = usb.openDevice(device)
                ?: throw PrinterException(PrintError.ConnectionFailed("could not open USB device"))
            if (!conn.claimInterface(intf, true)) {
                conn.close()
                throw PrinterException(PrintError.ConnectionFailed("USB interface busy"))
            }
            connection = conn
            claimed = intf
            outEndpoint = out
            inEndpoint = input
        }
    }

    private fun findEndpoints(device: UsbDevice): Triple<UsbInterface, UsbEndpoint, UsbEndpoint?>? {
        val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
        // Prefer the real printer class (7); fall back to any interface with a bulk-out pipe.
        val ordered = interfaces.sortedBy { if (it.interfaceClass == UsbConstants.USB_CLASS_PRINTER) 0 else 1 }
        for (intf in ordered) {
            val eps = (0 until intf.endpointCount).map { intf.getEndpoint(it) }
            val out = eps.firstOrNull {
                it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT
            } ?: continue
            val input = eps.firstOrNull {
                it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN
            }
            return Triple(intf, out, input)
        }
        return null
    }

    private suspend fun ensurePermission(device: UsbDevice) {
        if (usb.hasPermission(device)) return
        val granted = withTimeoutOrNull(PERMISSION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        runCatching { context.unregisterReceiver(this) }
                        if (cont.isActive) {
                            cont.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    context, receiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                // The system fills in extras, so the PendingIntent must be mutable on Android 12+.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pi = PendingIntent.getBroadcast(
                    context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags,
                )
                usb.requestPermission(device, pi)
            }
        }
        if (granted != true) throw PrinterException(PrintError.UsbPermissionDenied)
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val conn = connection ?: throw IOException("Not connected")
        val ep = outEndpoint ?: throw IOException("Not connected")
        var offset = 0
        while (offset < data.size) {
            val n = minOf(CHUNK, data.size - offset)
            val sent = conn.bulkTransfer(ep, data, offset, n, WRITE_TIMEOUT_MS)
            if (sent <= 0) throw IOException("USB write failed")
            offset += sent
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val conn = connection ?: throw IOException("Not connected")
        val ep = inEndpoint ?: return@withContext ByteArray(0) // some printers have no IN pipe
        val buf = ByteArray(maxOf(maxBytes, ep.maxPacketSize))
        val n = conn.bulkTransfer(ep, buf, buf.size, timeoutMs)
        if (n > 0) buf.copyOf(minOf(n, maxBytes)) else ByteArray(0)
    }

    override fun close() {
        runCatching { claimed?.let { connection?.releaseInterface(it) } }
        runCatching { connection?.close() }
        connection = null
        claimed = null
        outEndpoint = null
        inEndpoint = null
    }

    private companion object {
        const val ACTION_PERMISSION = "com.posprint.app.USB_PERMISSION"
        const val PERMISSION_TIMEOUT_MS = 30_000L
        const val WRITE_TIMEOUT_MS = 5_000
        const val CHUNK = 16 * 1024
    }
}
