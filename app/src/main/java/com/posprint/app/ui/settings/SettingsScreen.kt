package com.posprint.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.posprint.app.ui.common.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.BackupManager
import com.posprint.app.data.SettingsStore
import com.posprint.app.data.settings.BusinessProfile
import com.posprint.app.data.settings.DirectionMode
import com.posprint.app.data.settings.ThemeMode
import com.posprint.app.data.settings.UiPrefs
import com.posprint.app.ui.common.ChipRow
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.SectionTitle
import com.posprint.app.ui.common.Stepper
import com.posprint.app.ui.common.SwitchRow
import com.posprint.app.ui.common.FormCard
import com.posprint.app.ui.common.Hint
import com.posprint.app.ui.common.titleCase
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val backup: BackupManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    // Source of truth for the form is in memory so typing never fights the async store.
    private val _profile = MutableStateFlow(BusinessProfile())
    val profile: StateFlow<BusinessProfile> = _profile.asStateFlow()
    private val _ui = MutableStateFlow(UiPrefs())
    val ui: StateFlow<UiPrefs> = _ui.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            _profile.value = store.profile.first()
            _ui.value = store.ui.first()
        }
    }

    fun editProfile(change: (BusinessProfile) -> BusinessProfile) {
        _profile.update(change)
        viewModelScope.launch { store.saveProfile(_profile.value) }
    }

    fun editUi(change: (UiPrefs) -> UiPrefs) {
        _ui.update(change)
        viewModelScope.launch { store.saveUi(_ui.value) }
    }

    fun setLogo(uri: Uri) {
        viewModelScope.launch {
            val ok = store.setLogo(uri)
            _profile.update { it.copy(hasLogo = ok || it.hasLogo) }
            _messages.send(if (ok) "Logo saved." else "Could not read that image.")
        }
    }

    fun clearLogo() { viewModelScope.launch { store.clearLogo(); _profile.update { it.copy(hasLogo = false) } } }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)!!.use { backup.write(it) } }.isSuccess
            _messages.send(if (ok) "Backup saved." else "Could not save the backup.")
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            try {
                val r = context.contentResolver.openInputStream(uri)!!.use { backup.restore(it) }
                _profile.value = store.profile.first()
                _ui.value = store.ui.first()
                _messages.send("Restored ${r.printers} printer(s) and ${r.templates} template(s).")
            } catch (e: Exception) {
                _messages.send("That is not a valid backup file.")
            }
        }
    }
}

private val DATE_FORMATS = listOf("yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd MMM yyyy", "dd.MM.yyyy")
private val TIME_FORMATS = listOf("HH:mm", "hh:mm a", "HH:mm:ss")

@Composable
fun SettingsScreen(onDeveloper: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val p by vm.profile.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setLogo(uri) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(uri) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.restore(uri) }
    var confirmRestore by remember { mutableStateOf(false) }

    Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)

            SectionTitle("Business profile")
            Hint("Printed by the Header and Footer blocks of your templates.")
            FormCard {
                Field("Business name", p.name, { v -> vm.editProfile { it.copy(name = v) } })
                Field("Address", p.address, { v -> vm.editProfile { it.copy(address = v) } }, singleLine = false, minLines = 2)
                Field("Phone", p.phone, { v -> vm.editProfile { it.copy(phone = v) } })
                Field("Tax ID", p.taxId, { v -> vm.editProfile { it.copy(taxId = v) } })
                Field("Footer message", p.footer, { v -> vm.editProfile { it.copy(footer = v) } }, singleLine = false, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f).height(48.dp)) { Text(if (p.hasLogo) "Change logo" else "Add logo") }
                    if (p.hasLogo) OutlinedButton(vm::clearLogo, Modifier.weight(1f).height(48.dp)) { Text("Remove logo") }
                }
            }

            SectionTitle("Currency and formats")
            FormCard {
                Field("Currency symbol", p.currency, { v -> vm.editProfile { it.copy(currency = v.take(6)) } }, hint = "Example: ${p.formatter().money("1234.5")}")
                SwitchRow("Symbol after amount", p.currencyAfter) { v -> vm.editProfile { it.copy(currencyAfter = v) } }
                Stepper("Decimal places", p.decimals, 0..4) { v -> vm.editProfile { it.copy(decimals = v) } }
                SwitchRow("Thousands separator", p.thousands, supporting = "1,234.50 instead of 1234.50") { v -> vm.editProfile { it.copy(thousands = v) } }
                Dropdown("Date format", DATE_FORMATS, p.datePattern, ::sampleDate) { v -> vm.editProfile { it.copy(datePattern = v) } }
                Dropdown("Time format", TIME_FORMATS, p.timePattern, ::sampleTime) { v -> vm.editProfile { it.copy(timePattern = v) } }
                Field("Default tax % (Mini POS)", p.defaultTaxPct, { v -> vm.editProfile { it.copy(defaultTaxPct = v) } }, number = true)
            }

            SectionTitle("Appearance")
            FormCard {
                ChipRow("Theme", ThemeMode.entries, ui.theme, { titleCase(it.name) }) { v -> vm.editUi { it.copy(theme = v) } }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    SwitchRow("Dynamic colour", ui.dynamicColor, supporting = "Match your wallpaper colours") { v -> vm.editUi { it.copy(dynamicColor = v) } }
                }
                ChipRow("Layout direction", DirectionMode.entries, ui.direction, { when (it) { DirectionMode.AUTO -> "Automatic"; DirectionMode.LTR -> "Left to right"; DirectionMode.RTL -> "Right to left" } }) { v -> vm.editUi { it.copy(direction = v) } }
            }

            SectionTitle("Paper printing defaults")
            FormCard {
                Stepper("Margins", ui.paperMarginMm, 0..40, valueText = "${ui.paperMarginMm} mm") { v -> vm.editUi { it.copy(paperMarginMm = v) } }
                Stepper("Text scale", ui.paperScalePct, 50..200, step = 10, valueText = "${ui.paperScalePct}%") { v -> vm.editUi { it.copy(paperScalePct = v) } }
            }

            SectionTitle("Backup and restore")
            FormCard {
                Hint("One file with printers, templates, business profile and preferences. In the file picker you can choose local storage or Google Drive.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ exporter.launch("posprint-backup.json") }, Modifier.weight(1f).height(48.dp)) { Text("Back up") }
                    OutlinedButton({ confirmRestore = true }, Modifier.weight(1f).height(48.dp)) { Text("Restore") }
                }
            }

            SectionTitle("Advanced")
            FormCard {
                OutlinedButton(onDeveloper, Modifier.fillMaxWidth().height(48.dp)) { Text("Developer tools") }
                Hint("Raw hex console, printer status query and the command log.")
            }
            Text(
                "POS Print ${appVersion()} · works offline, no account needed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (confirmRestore) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore a backup?") },
            text = { Text("Printers and templates in the file are merged in; ones with the same name are replaced. Your business profile and preferences are overwritten.") },
            confirmButton = {
                com.posprint.app.ui.common.TextButton({ confirmRestore = false; importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Choose file") }
            },
            dismissButton = { com.posprint.app.ui.common.TextButton({ confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

private val sampleNow = java.time.LocalDateTime.of(2026, 3, 9, 14, 30, 5)
private fun sampleDate(pattern: String) = runCatching { "${sampleNow.format(java.time.format.DateTimeFormatter.ofPattern(pattern))}  ($pattern)" }.getOrDefault(pattern)
private fun sampleTime(pattern: String) = runCatching { "${sampleNow.format(java.time.format.DateTimeFormatter.ofPattern(pattern))}  ($pattern)" }.getOrDefault(pattern)

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
}
