package com.posprint.app.data.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.DiscoveredDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Callers must hold the Bluetooth runtime permissions (see util/Permissions.kt). */
@Singleton
@SuppressLint("MissingPermission")
class BluetoothDiscovery @Inject constructor(@ApplicationContext private val context: Context) {

    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun bonded(): List<DiscoveredDevice> =
        adapter?.bondedDevices.orEmpty().map { it.toDiscovered(paired = true) }.sortedForDisplay()

    /** Emits each device as it is found; completes when the discovery window closes. */
    fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        val a = adapter
        if (a == null || !a.isEnabled) { close(); return@callbackFlow }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> IntentCompat
                        .getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        ?.let { trySend(it.toDiscovered(paired = it.bondState == BluetoothDevice.BOND_BONDED)) }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        a.cancelDiscovery()
        a.startDiscovery()
        awaitClose {
            runCatching { a.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /** Bonds with the device. Returns true if already paired or pairing succeeded within 60 s. */
    suspend fun pair(address: String): Boolean {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return false
        if (device.bondState == BluetoothDevice.BOND_BONDED) return true
        return withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        if (d?.address != address) return
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        if (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_NONE) {
                            runCatching { context.unregisterReceiver(this) }
                            if (cont.isActive) cont.resume(state == BluetoothDevice.BOND_BONDED)
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                if (!device.createBond()) {
                    runCatching { context.unregisterReceiver(receiver) }
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
    }

    private fun BluetoothDevice.toDiscovered(paired: Boolean): DiscoveredDevice {
        val label = name?.takeIf { it.isNotBlank() } ?: address
        val imaging = bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.IMAGING
        return DiscoveredDevice(
            name = label,
            address = address,
            connection = ConnectionType.BLUETOOTH,
            paired = paired,
            likelyPrinter = imaging || PRINTER_NAME.containsMatchIn(label),
        )
    }

    companion object {
        private val PRINTER_NAME = Regex(
            "print|pos|receipt|thermal|xp-|rpp|mpt|mtp|pt-|spp|tm-|bt-?\\d|gprinter|rongta|epson|zjiang|goojprt|munbyn|hoin|sunmi",
            RegexOption.IGNORE_CASE,
        )
    }
}

/** Likely printers first, then paired, then by name. */
fun List<DiscoveredDevice>.sortedForDisplay(): List<DiscoveredDevice> =
    sortedWith(compareByDescending<DiscoveredDevice> { it.likelyPrinter }.thenByDescending { it.paired }.thenBy { it.name.lowercase() })
