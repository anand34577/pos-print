package com.posprint.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.posprint.app.domain.model.CodePage
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.PaperWidth
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterLanguage

@Entity(tableName = "printers", indices = [Index(value = ["address"], unique = true)])
data class PrinterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val connection: ConnectionType,
    val address: String,
    val language: PrinterLanguage,
    val paperWidth: PaperWidth,
    val hasCutter: Boolean,
    val hasDrawer: Boolean,
    val isDefault: Boolean,
    val codePage: CodePage,
    val dpi: Int,
    val charsOverride: Int?,
    val density: Int?,
    val lineSpacing: Int?,
    val marginDots: Int,
)

fun PrinterEntity.toDomain() = Printer(
    id, name, connection, address, language, paperWidth, hasCutter, hasDrawer, isDefault,
    codePage, dpi, charsOverride, density, lineSpacing, marginDots,
)

fun Printer.toEntity() = PrinterEntity(
    id, name, connection, address, language, paperWidth, hasCutter, hasDrawer, isDefault,
    codePage, dpi, charsOverride, density, lineSpacing, marginDots,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val builtIn: Boolean,
    val updatedAt: Long,
    /** The whole [com.posprint.app.domain.template.Template] as JSON. */
    val json: String,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val printerId: Long,
    val printerName: String,
    val createdAt: Long,
    val success: Boolean,
    val error: String?,
    /** Raw bytes sent, kept (up to 512 KB) so the job can be reprinted. Null when too large. */
    val payload: ByteArray?,
)
