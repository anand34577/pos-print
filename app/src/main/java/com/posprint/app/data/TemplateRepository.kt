package com.posprint.app.data

import com.posprint.app.data.db.TemplateDao
import com.posprint.app.data.db.TemplateEntity
import com.posprint.app.domain.template.Alignment
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.DividerBlock
import com.posprint.app.domain.template.FooterBlock
import com.posprint.app.domain.template.HeaderBlock
import com.posprint.app.domain.template.LBarcode
import com.posprint.app.domain.template.LText
import com.posprint.app.domain.template.LabelSpec
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateData
import com.posprint.app.domain.template.TemplateJson
import com.posprint.app.domain.template.TemplateKind
import com.posprint.app.domain.template.TextBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepository @Inject constructor(
    private val dao: TemplateDao,
    private val settings: SettingsStore,
) {
    private fun TemplateEntity.toTemplate() =
        TemplateJson.decode(json).copy(id = id, name = name, builtIn = builtIn)

    fun observeAll(): Flow<List<Template>> = dao.observeAll().map { l -> l.map { it.toTemplate() } }
    suspend fun get(id: Long): Template? = dao.get(id)?.toTemplate()

    /** Inserts when [Template.id] is 0, otherwise updates. Returns the id. */
    suspend fun save(t: Template): Long {
        val e = TemplateEntity(t.id, t.name, t.kind.name, t.builtIn, System.currentTimeMillis(), TemplateJson.encode(t.copy(id = 0)))
        return if (t.id == 0L) dao.insert(e) else { dao.update(e); t.id }
    }

    suspend fun duplicate(id: Long): Long? =
        get(id)?.let { save(it.copy(id = 0, name = "${it.name} copy", builtIn = false)) }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Creates and stores a small starter template of [kind]. */
    suspend fun createBlank(kind: com.posprint.app.domain.template.TemplateKind): Template {
        val t = when (kind) {
            TemplateKind.RECEIPT -> Template(name = "New receipt", kind = kind, blocks = listOf(
                HeaderBlock(), DividerBlock(), TextBlock("Hello {{name}}", Alignment.CENTER), FooterBlock()),
                sample = TemplateData(mapOf("name" to "friend")))
            TemplateKind.DOCUMENT -> Template(name = "New document", kind = kind, blocks = listOf(
                HeaderBlock(), DividerBlock("="), TextBlock("Document for {{name}}"), FooterBlock()),
                sample = TemplateData(mapOf("name" to "friend")))
            TemplateKind.LABEL -> Template(name = "New label", kind = kind, label = LabelSpec(50.0, 30.0), elements = listOf(
                LText("n1", 2.0, 2.0, 46.0, 7.0, 0, "{{product_name}}", true, Alignment.LEFT, 4.0),
                LBarcode("n2", 4.0, 11.0, 42.0, 16.0, 0, "{{sku}}", BarcodeType.CODE128, true)),
                sample = TemplateData(mapOf("product_name" to "Sample product", "sku" to "123456789012")))
        }
        return t.copy(id = save(t))
    }

    fun export(t: Template): String = TemplateJson.encode(t.copy(id = 0, builtIn = false))

    /** Throws if [json] is not a template. */
    suspend fun import(json: String): Long = save(TemplateJson.decode(json).copy(id = 0, builtIn = false))

    suspend fun seedIfFirstLaunch() {
        if (settings.seeded.first()) return
        if (dao.count() == 0) StarterTemplates.all().forEach { save(it) }
        settings.markSeeded()
    }
}
