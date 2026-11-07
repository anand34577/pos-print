package com.posprint.app.domain.model

/** User-facing failure reasons. [message] is safe to show to non-technical users. */
sealed class PrintError(val message: String, val retryable: Boolean = false) {
    data object BluetoothPermissionDenied :
        PrintError("Bluetooth permission was denied. Allow \"Nearby devices\" for this app in Settings.")

    data object UsbPermissionDenied :
        PrintError("USB access was denied. Unplug the printer, plug it in again and tap Allow.")

    data object BluetoothOff : PrintError("Bluetooth is turned off. Turn it on and try again.")
    data object DeviceNotFound :
        PrintError("Printer not found. Check that it is switched on, in range or plugged in.")

    data object NoUsbEndpoint :
        PrintError("This USB device does not look like a printer (no data endpoint).")

    data class ConnectionFailed(val detail: String?) : PrintError(
        "Could not connect to the printer. Make sure it is on, paired and not used by another phone." +
            (detail?.let { " ($it)" } ?: ""),
        retryable = true,
    )

    data class WriteFailed(val detail: String?) : PrintError(
        "Lost connection while printing." + (detail?.let { " ($it)" } ?: ""),
        retryable = true,
    )

    data object PaperOut : PrintError("Printer is out of paper. Load a new roll, then tap Retry.")
    data object CoverOpen : PrintError("Printer cover is open. Close it, then tap Retry.")
    data object Offline : PrintError("Printer reports an error or is offline. Check it and tap Retry.")
    data class NoPrinter(val hint: String = "Add a printer first.") : PrintError("No printer available. $hint")
    data class Other(val text: String) : PrintError(text)
}

class PrinterException(val error: PrintError, cause: Throwable? = null) :
    Exception(error.message, cause)

/** Decoded real-time status reply (ESC/POS DLE EOT). */
data class PrinterStatus(
    val coverOpen: Boolean = false,
    val paperOut: Boolean = false,
    val paperNearEnd: Boolean = false,
    val errorFlag: Boolean = false,
) {
    fun toError(): PrintError? = when {
        coverOpen -> PrintError.CoverOpen
        paperOut -> PrintError.PaperOut
        errorFlag -> PrintError.Offline
        else -> null
    }
}

enum class JobStatus { QUEUED, PRINTING, DONE, FAILED, CANCELLED }

data class PrintJob(
    val id: Long,
    val printerId: Long,
    val title: String,
    val status: JobStatus = JobStatus.QUEUED,
    val attempts: Int = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
