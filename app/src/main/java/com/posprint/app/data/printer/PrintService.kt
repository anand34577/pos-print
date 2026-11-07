package com.posprint.app.data.printer

import com.posprint.app.data.db.HistoryDao
import com.posprint.app.data.db.HistoryEntity
import com.posprint.app.di.ApplicationScope
import com.posprint.app.domain.model.JobStatus
import com.posprint.app.domain.model.PrintError
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.model.PrinterStatus
import com.posprint.app.domain.printer.readFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash-safe print queue. Jobs run one at a time on a background coroutine, never on the main
 * thread, retry on transient link errors, and stop with a plain-language reason when the printer
 * needs a human (paper out, cover open). Finished jobs are written to the history table for reprint.
 */
@Singleton
class PrintService @Inject constructor(
    private val connections: PrinterConnectionManager,
    private val drivers: DriverRegistry,
    private val history: HistoryDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private class Payload(val printer: Printer, val data: ByteArray)

    private val nextId = AtomicLong(1)
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val payloads = ConcurrentHashMap<Long, Payload>()
    private val _jobs = MutableStateFlow<List<PrintJob>>(emptyList())

    /** Newest first. */
    val jobs: StateFlow<List<PrintJob>> = _jobs.asStateFlow()

    init {
        scope.launch {
            for (id in queue) {
                try {
                    process(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    finish(id, false, e.message ?: "Unexpected error")
                }
            }
        }
    }

    fun enqueue(printer: Printer, title: String, data: ByteArray): Long {
        val id = nextId.getAndIncrement()
        payloads[id] = Payload(printer, data)
        _jobs.update { (listOf(PrintJob(id, printer.id, title)) + it).take(MAX_JOBS) }
        queue.trySend(id)
        return id
    }

    fun enqueueTestPage(printer: Printer) =
        enqueue(printer, "Test print", drivers.forLanguage(printer.language).testPage(printer))

    /** Null when the printer's language has no built-in self-test. */
    fun enqueueSelfTest(printer: Printer): Long? {
        val bytes = drivers.forLanguage(printer.language).selfTest()
        return if (bytes.isEmpty()) null else enqueue(printer, "Printer self-test", bytes)
    }

    fun retry(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.status != JobStatus.FAILED && job.status != JobStatus.CANCELLED) return
        if (!payloads.containsKey(id)) return
        update(id) { it.copy(status = JobStatus.QUEUED, error = null, attempts = 0) }
        queue.trySend(id)
    }

    fun cancel(id: Long) {
        update(id) { if (it.status == JobStatus.QUEUED) it.copy(status = JobStatus.CANCELLED) else it }
    }

    /** Null means the printer did not answer a status query (normal for many clones and all label printers). */
    suspend fun checkStatus(printer: Printer): PrinterStatus? {
        val driver = drivers.forLanguage(printer.language)
        if (driver.statusRequest.isEmpty()) return null
        return connections.use(printer) { t ->
            t.write(driver.statusRequest)
            driver.parseStatus(t.readFully(driver.statusReplySize, STATUS_TIMEOUT_MS))
        }
    }

    /** Sends bytes right now (developer console), bypassing the queue. */
    suspend fun sendRaw(printer: Printer, data: ByteArray) = connections.use(printer) { it.write(data) }

    private suspend fun process(id: Long) {
        val payload = payloads[id] ?: return
        if (_jobs.value.firstOrNull { it.id == id }?.status != JobStatus.QUEUED) return
        update(id) { it.copy(status = JobStatus.PRINTING) }

        var lastError: PrintError? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            update(id) { it.copy(attempts = attempt) }
            try {
                checkStatus(payload.printer)?.toError()?.let { throw PrinterException(it) }
                connections.use(payload.printer) { it.write(payload.data) }
                finish(id, true, null)
                payloads.remove(id)
                return
            } catch (e: PrinterException) {
                lastError = e.error
                if (!e.error.retryable) break
            }
            if (attempt < MAX_ATTEMPTS) delay(attempt * 1_000L)
        }
        finish(id, false, lastError?.message)
    }

    private fun finish(id: Long, ok: Boolean, error: String?) {
        update(id) { it.copy(status = if (ok) JobStatus.DONE else JobStatus.FAILED, error = error) }
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        val p = payloads[id] ?: return
        scope.launch {
            runCatching {
                history.insert(HistoryEntity(
                    title = job.title, printerId = p.printer.id, printerName = p.printer.name, createdAt = job.createdAt,
                    success = ok, error = error, payload = p.data.takeIf { it.size <= MAX_STORED_BYTES },
                ))
                history.trim()
            }
        }
    }

    private fun update(id: Long, transform: (PrintJob) -> PrintJob) =
        _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val MAX_JOBS = 50
        const val STATUS_TIMEOUT_MS = 400
        const val MAX_STORED_BYTES = 512 * 1024
    }
}
