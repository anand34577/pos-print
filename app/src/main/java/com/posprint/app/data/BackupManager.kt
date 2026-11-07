package com.posprint.app.data

import android.util.Base64
import com.posprint.app.data.settings.BusinessProfile
import com.posprint.app.data.settings.UiPrefs
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupFile(
    val version: Int = 1,
    val createdAt: Long,
    val printers: List<Printer>,
    val templates: List<Template>,
    val profile: BusinessProfile,
    val ui: UiPrefs,
    val logoPngBase64: String? = null,
)

data class RestoreResult(val printers: Int, val templates: Int)

/**
 * One JSON file with printers, templates, business profile and preferences. Written and read through
 * the system file picker, so the destination can be local storage or any cloud provider (e.g. Google Drive)
 * that the picker offers; the app itself needs no account.
 */
@Singleton
class BackupManager @Inject constructor(
    private val printers: PrinterRepository,
    private val templates: TemplateRepository,
    private val settings: SettingsStore,
) {
    suspend fun write(out: OutputStream) {
        val logo = settings.logoFile.takeIf { it.exists() }?.readBytes()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val file = BackupFile(
            createdAt = System.currentTimeMillis(),
            printers = printers.observeAll().first(),
            templates = templates.observeAll().first(),
            profile = settings.profileNow(),
            ui = settings.ui.first(),
            logoPngBase64 = logo,
        )
        withContext(Dispatchers.IO) { out.write(TemplateJson.json.encodeToString(BackupFile.serializer(), file).toByteArray()) }
    }

    /** Merges the backup into current data: same printer address or template name+kind is overwritten. Throws on a bad file. */
    suspend fun restore(input: InputStream): RestoreResult {
        val text = withContext(Dispatchers.IO) { input.bufferedReader().readText() }
        val file = TemplateJson.json.decodeFromString(BackupFile.serializer(), text)
        require(file.version <= 1) { "This backup was made by a newer version of the app." }

        var defaultAddress: String? = null
        file.printers.forEach { p ->
            val existing = printers.findByAddress(p.address)
            if (existing != null) printers.update(p.copy(id = existing.id, isDefault = existing.isDefault))
            else printers.add(p.copy(id = 0))
            if (p.isDefault) defaultAddress = p.address
        }
        defaultAddress?.let { a -> printers.findByAddress(a)?.let { printers.setDefault(it.id) } }

        val current = templates.observeAll().first()
        file.templates.forEach { t ->
            val same = current.firstOrNull { it.name == t.name && it.kind == t.kind }
            templates.save(t.copy(id = same?.id ?: 0))
        }
        settings.saveProfile(file.profile)
        settings.saveUi(file.ui)
        file.logoPngBase64?.let { settings.setLogoBytes(Base64.decode(it, Base64.DEFAULT)) }
        settings.markSeeded()
        return RestoreResult(file.printers.size, file.templates.size)
    }
}
