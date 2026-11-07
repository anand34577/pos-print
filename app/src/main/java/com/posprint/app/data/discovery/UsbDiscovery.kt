package com.posprint.app.data.discovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.DiscoveredDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbDiscovery @Inject constructor(@ApplicationContext private val context: Context) {

    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun devices(): List<DiscoveredDevice> =
        usb.deviceList.values.map { it.toDiscovered() }.sortedForDisplay()

    /** Current list, re-emitted whenever a USB device is plugged in or removed. */
    fun observe(): Flow<List<DiscoveredDevice>> = callbackFlow {
        trySend(devices())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { trySend(devices()) }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    private fun UsbDevice.toDiscovered(): DiscoveredDevice {
        val isPrinter = deviceClass == UsbConstants.USB_CLASS_PRINTER ||
            (0 until interfaceCount).any { getInterface(it).interfaceClass == UsbConstants.USB_CLASS_PRINTER }
        val label = productName?.takeIf { it.isNotBlank() }
            ?: "USB device %04X:%04X".format(vendorId, productId)
        return DiscoveredDevice(
            name = label,
            address = "$vendorId:$productId",
            connection = ConnectionType.USB,
            paired = true, // no pairing step on USB
            likelyPrinter = isPrinter,
        )
    }
}
