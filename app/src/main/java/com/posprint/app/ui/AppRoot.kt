package com.posprint.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.posprint.app.data.Shared
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateKind
import com.posprint.app.ui.addprinter.AddPrinterScreen
import com.posprint.app.ui.file.FilePrintScreen
import com.posprint.app.ui.history.HistoryScreen
import com.posprint.app.ui.home.HomeScreen
import com.posprint.app.ui.labels.LabelDesignerScreen
import com.posprint.app.ui.labels.LabelPrintScreen
import com.posprint.app.ui.paper.PaperScreen
import com.posprint.app.ui.pos.MiniPosScreen
import com.posprint.app.ui.printers.PrintersScreen
import com.posprint.app.ui.quick.QuickPrintScreen
import com.posprint.app.ui.scan.ScanScreen
import com.posprint.app.ui.settings.DeveloperScreen
import com.posprint.app.ui.settings.SettingsScreen
import com.posprint.app.ui.templates.PrintTemplateScreen
import com.posprint.app.ui.templates.TemplateEditorScreen
import com.posprint.app.ui.templates.TemplatesScreen
import kotlinx.coroutines.launch

private object Routes {
    const val ONBOARDING = "onboarding"
    const val ADD_PRINTER = "add_printer"
    const val HOME = "home"
    const val TEMPLATES = "templates"
    const val PRINTERS = "printers"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Routes.TEMPLATES, "Templates", Icons.Outlined.Description, Icons.Rounded.Description),
    Tab(Routes.PRINTERS, "Printers", Icons.Outlined.Print, Icons.Rounded.Print),
    Tab(Routes.HISTORY, "History", Icons.Outlined.History, Icons.Rounded.History),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

private val idArg = listOf(navArgument("id") { type = NavType.LongType })
private val editorArgs = listOf(
    navArgument("id") { type = NavType.LongType },
    navArgument("fresh") { type = NavType.BoolType; defaultValue = false },
)

/** Where "print this template" goes for each kind. */
private fun printRoute(kind: TemplateKind, id: Long) = when (kind) {
    TemplateKind.RECEIPT -> "print_template/$id"
    TemplateKind.LABEL -> "label_print/$id"
    TemplateKind.DOCUMENT -> "paper/$id"
}

/** Editor for a template; [fresh] marks one just created, which is discarded if left without saving. */
private fun editRoute(kind: TemplateKind, id: Long, fresh: Boolean = false) =
    (if (kind == TemplateKind.LABEL) "label_designer/$id" else "template_editor/$id") + "?fresh=$fresh"

private fun NavController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppRoot(vm: MainViewModel = hiltViewModel()) {
    // Resolve the start route once; later changes must not re-seat the nav graph.
    val start by produceState<String?>(null) { value = vm.resolveStartRoute() }
    val startRoute = start ?: return

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val onTab = tabs.any { it.route == current }

    // Content shared from other apps jumps straight to the right print screen.
    val pending by vm.share.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        when (val s = pending) {
            is Shared.Text -> nav.navigate("quick_print?text=${Uri.encode(s.text)}")
            is Shared.File -> nav.navigate("print_file?uri=${Uri.encode(s.uri)}&mime=${Uri.encode(s.mime)}")
            null -> Unit
        }
        if (pending != null) vm.share.consume()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            nav.navigate("print_file?uri=${Uri.encode(uri.toString())}&mime=${Uri.encode(mime)}")
        }
    }

    val openNew: (Template) -> Unit = { t -> nav.navigate(editRoute(t.kind, t.id, fresh = true)) }

    Scaffold(
        // Each screen owns its own top insets (a TopAppBar, or statusBarsPadding on tabs). Applying them here too
        // produced a double status-bar gap above every inner screen.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            nav, startDestination = startRoute,
            // consumeWindowInsets lets inner TopAppBars/imePadding subtract what the bottom bar already covers.
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
        ) {
            composable(Routes.ONBOARDING) {
                val toHome = { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
                AddPrinterScreen(onClose = toHome, onDone = toHome, onboarding = true)
            }
            composable(Routes.ADD_PRINTER) {
                AddPrinterScreen(onClose = { nav.popBackStack() }, onDone = { nav.popBackStack() })
            }
            tab(Routes.HOME) {
                HomeScreen(
                    onAddPrinter = { nav.navigate(Routes.ADD_PRINTER) },
                    onQuickPrint = { nav.navigate("quick_print?text=") },
                    onNewLabel = { scope.launch { openNew(vm.newLabel()) } },
                    onScan = { nav.navigate("scan") },
                    onMiniPos = { nav.navigate("mini_pos") },
                    onPrintFile = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                    onHistory = { nav.switchTab(Routes.HISTORY) },
                    onPrinters = { nav.switchTab(Routes.PRINTERS) },
                )
            }
            tab(Routes.PRINTERS) { PrintersScreen(onAddPrinter = { nav.navigate(Routes.ADD_PRINTER) }) }
            tab(Routes.HISTORY) { HistoryScreen() }
            tab(Routes.SETTINGS) { SettingsScreen(onDeveloper = { nav.navigate("developer") }) }
            composable("developer") { DeveloperScreen(onBack = { nav.popBackStack() }) }

            tab(Routes.TEMPLATES) {
                TemplatesScreen(
                    onEdit = { t -> nav.navigate(editRoute(t.kind, t.id)) },
                    onCreated = openNew,
                    onPrint = { t -> nav.navigate(printRoute(t.kind, t.id)) },
                )
            }
            composable("template_editor/{id}?fresh={fresh}", editorArgs) {
                TemplateEditorScreen(
                    onBack = { nav.popBackStack() },
                    // Replace the editor with the print screen so Back returns to the template list.
                    onPrint = { t -> nav.navigate(printRoute(t.kind, t.id)) { popUpTo("template_editor/{id}?fresh={fresh}") { inclusive = true } } },
                )
            }
            composable("print_template/{id}", idArg) { PrintTemplateScreen(onBack = { nav.popBackStack() }) }
            composable("label_designer/{id}?fresh={fresh}", editorArgs) {
                LabelDesignerScreen(
                    onBack = { nav.popBackStack() },
                    onPrint = { id -> nav.navigate("label_print/$id") { popUpTo("label_designer/{id}?fresh={fresh}") { inclusive = true } } },
                )
            }
            composable(
                "label_print/{id}?scan={scan}",
                listOf(navArgument("id") { type = NavType.LongType }, navArgument("scan") { type = NavType.StringType; defaultValue = "" }),
            ) { LabelPrintScreen(onBack = { nav.popBackStack() }) }
            composable("paper/{id}", idArg) { PaperScreen(onBack = { nav.popBackStack() }) }
            composable("scan") {
                ScanScreen(onBack = { nav.popBackStack() }, onPrint = { id, v -> nav.navigate("label_print/$id?scan=${Uri.encode(v)}") })
            }
            composable("mini_pos") { MiniPosScreen(onBack = { nav.popBackStack() }) }
            composable(
                "quick_print?text={text}",
                listOf(navArgument("text") { type = NavType.StringType; defaultValue = "" }),
            ) { QuickPrintScreen(onBack = { nav.popBackStack() }) }
            composable(
                "print_file?uri={uri}&mime={mime}",
                listOf(navArgument("uri") { type = NavType.StringType }, navArgument("mime") { type = NavType.StringType; defaultValue = "" }),
            ) { FilePrintScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

/** Top-level tab destinations have no TopAppBar, so they pad themselves below the status bar. */
private fun NavGraphBuilder.tab(route: String, content: @Composable () -> Unit) =
    composable(route) { Box(Modifier.fillMaxSize().statusBarsPadding()) { content() } }
