package com.posprint.app.data.printer

import com.posprint.app.domain.printer.PrinterTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class LogEntry(val time: Long, val printer: String, val outgoing: Boolean, val bytes: ByteArray)

/** Last 200 transfers, for the developer console. Only the first 512 bytes of each are kept. */
@Singleton
class CommandLog @Inject constructor() {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(printer: String, outgoing: Boolean, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        _entries.update { (it + LogEntry(System.currentTimeMillis(), printer, outgoing, bytes.copyOf(minOf(bytes.size, 512)))).takeLast(200) }
    }

    fun clear() { _entries.value = emptyList() }
}

class LoggingTransport(private val inner: PrinterTransport, private val name: String, private val log: CommandLog) : PrinterTransport by inner {
    override suspend fun write(data: ByteArray) {
        log.add(name, true, data)
        inner.write(data)
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Int): ByteArray =
        inner.read(maxBytes, timeoutMs).also { log.add(name, false, it) }
}
