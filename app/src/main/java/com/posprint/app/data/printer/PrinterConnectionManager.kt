package com.posprint.app.data.printer

import com.posprint.app.data.transport.TransportFactory
import com.posprint.app.domain.model.ConnectionState
import com.posprint.app.domain.model.PrintError
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.printer.PrinterTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns one transport per printer, serialises access to it, and reconnects once if the link
 * has gone stale (printer slept, phone walked out of range, USB re-plugged).
 */
@Singleton
class PrinterConnectionManager @Inject constructor(
    private val transports: TransportFactory,
    private val log: CommandLog,
) {

    private class Session(val transport: PrinterTransport, val mutex: Mutex = Mutex())

    private val sessions = ConcurrentHashMap<Long, Session>()
    private val _states = MutableStateFlow<Map<Long, ConnectionState>>(emptyMap())
    val states: StateFlow<Map<Long, ConnectionState>> = _states.asStateFlow()

    private fun setState(id: Long, state: ConnectionState) = _states.update { it + (id to state) }

    private fun session(printer: Printer) =
        sessions.computeIfAbsent(printer.id) { Session(LoggingTransport(transports.create(printer), printer.name, log)) }

    /** Opens the link if it is not already open. Throws [PrinterException]. */
    suspend fun connect(printer: Printer) {
        val s = session(printer)
        s.mutex.withLock { ensureOpen(printer, s) }
    }

    /** Runs [block] against an open transport, reconnecting once on a stale link. */
    suspend fun <T> use(printer: Printer, block: suspend (PrinterTransport) -> T): T {
        val s = session(printer)
        return s.mutex.withLock {
            try {
                ensureOpen(printer, s)
                block(s.transport)
            } catch (first: IOException) {
                s.transport.close()
                setState(printer.id, ConnectionState.DISCONNECTED)
                try {
                    ensureOpen(printer, s)
                    block(s.transport)
                } catch (second: IOException) {
                    s.transport.close()
                    setState(printer.id, ConnectionState.ERROR)
                    throw PrinterException(PrintError.WriteFailed(second.message), second)
                }
            }
        }
    }

    fun disconnect(printerId: Long) {
        sessions.remove(printerId)?.transport?.close()
        _states.update { it - printerId }
    }

    private suspend fun ensureOpen(printer: Printer, s: Session) {
        if (s.transport.isOpen) {
            setState(printer.id, ConnectionState.CONNECTED)
            return
        }
        setState(printer.id, ConnectionState.CONNECTING)
        try {
            s.transport.open()
            setState(printer.id, ConnectionState.CONNECTED)
        } catch (e: PrinterException) {
            setState(printer.id, ConnectionState.ERROR)
            throw e
        } catch (e: IOException) {
            setState(printer.id, ConnectionState.ERROR)
            throw PrinterException(PrintError.ConnectionFailed(e.message), e)
        }
    }
}
