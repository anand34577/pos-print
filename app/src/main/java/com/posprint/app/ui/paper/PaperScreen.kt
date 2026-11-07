package com.posprint.app.ui.paper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PictureAsPdf
import com.posprint.app.ui.common.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.posprint.app.ui.common.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.data.print.PaperSize
import com.posprint.app.domain.render.TemplateEngine
import com.posprint.app.domain.template.TableBlock
import com.posprint.app.ui.common.ChipRow
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.SectionTitle
import com.posprint.app.ui.common.Stepper
import com.posprint.app.ui.common.isWideScreen
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** A4 / A5 / Letter output: live page preview, margins, scale, orientation, system print dialog, PDF export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperScreen(onBack: () -> Unit, vm: PaperViewModel = hiltViewModel()) {
    val t by vm.template.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val o by vm.options.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val calculated by vm.calculated.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    val template = t

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null) vm.export(uri) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(template?.name ?: "Print on paper") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (template == null) return@Scaffold
        val form: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChipRow("Paper", PaperSize.entries, o.size, { it.label }) { s -> vm.options { it.copy(size = s) } }
                ChipRow("Orientation", listOf(false, true), o.landscape, { if (it) "Landscape" else "Portrait" }) { l -> vm.options { it.copy(landscape = l) } }
                Stepper("Margins (mm)", o.marginMm, 0..40) { m -> vm.options { it.copy(marginMm = m) } }
                Stepper("Text scale", o.scalePct, 50..200, step = 10, valueText = "${o.scalePct}%") { sc -> vm.options { it.copy(scalePct = sc) } }
                val lists = template.blocks.filterIsInstance<TableBlock>().associate { it.source to it.columns.map { c -> c.field } }
                val vars = TemplateEngine.variablesUsed(template.blocks).filterNot { it in lists }
                lists.forEach { (src, fields) ->
                    com.posprint.app.ui.common.ItemsEditor(src, fields, data.lists[src].orEmpty()) { vm.setList(src, it) }
                }
                val typed = vars.filterNot { it in calculated }
                if (typed.isNotEmpty()) SectionTitle("Details")
                typed.forEach { n -> Field(com.posprint.app.ui.common.humanize(n), data.vars[n].orEmpty(), { vm.setVar(n, it) }) }
                com.posprint.app.ui.common.CalculatedSummary(vars.filter { it in calculated }.map { it to data.vars[it].orEmpty() })
                Button({ context.findActivity()?.let { a -> scope.launch { vm.print(a) } } }, Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Rounded.Print, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                    Text("Print…")
                }
                OutlinedButton({ exporter.launch("${template.name.replace(Regex("[\\\\/:*?\"<>|]"), "")}.pdf") }, Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.PictureAsPdf, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                    Text("Save as PDF")
                }
                Text("Print… opens Android's print dialog: choose a Wi-Fi, USB or Bluetooth office printer there, plus page range and copies.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val previewPane: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                view.image?.let { img ->
                    Image(img, "Page ${page + 1} of ${view.pageCount}", Modifier.fillMaxWidth().shadow(2.dp).background(Color.White), contentScale = ContentScale.FillWidth)
                }
                if (view.pageCount > 1) {
                    Stepper("Page ${page + 1} of ${view.pageCount}", page + 1, 1..view.pageCount) { vm.page(it - 1) }
                }
            }
        }
        if (isWideScreen()) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { form() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { previewPane() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                androidx.compose.material3.PrimaryTabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Setup") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Preview") })
                }
                Column(Modifier.verticalScroll(rememberScrollState())) { if (tab == 0) form() else previewPane() }
            }
        }
    }
}
