package com.posprint.app.domain.printer

import kotlinx.coroutines.delay
import java.io.Closeable

/**
 * A raw byte pipe to one printer (Bluetooth RFCOMM, USB bulk, later TCP 9100).
 * All methods are main-safe. IO failures after [open] surface as IOException;
 * failures inside [open] surface as PrinterException.
 */
interface PrinterTransport : Closeable {
    val isOpen: Boolean
    suspend fun open()
    suspend fun write(data: ByteArray)

    /** Returns whatever arrives within [timeoutMs], up to [maxBytes]; empty if nothing came. */
    suspend fun read(maxBytes: Int, timeoutMs: Int): ByteArray
}

/** Reads until [count] bytes arrive or [timeoutMs] elapses. */
suspend fun PrinterTransport.readFully(count: Int, timeoutMs: Int): ByteArray {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000L
    var buf = ByteArray(0)
    while (buf.size < count) {
        val left = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
        if (left <= 0) break
        buf += read(count - buf.size, minOf(left, 100))
        if (buf.size < count) delay(10)
    }
    return buf
}
