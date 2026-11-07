package com.posprint.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Print
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.posprint.app.domain.model.ConnectionState
import com.posprint.app.domain.model.JobStatus
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.ui.common.Stepper
import com.posprint.app.ui.components.ConnectionChip
import com.posprint.app.ui.components.EmptyState
import com.posprint.app.ui.components.JobRow
import com.posprint.app.ui.theme.PosPrintTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Core print-feedback flows, exercised on real Compose UI (run with `./gradlew connectedDebugAndroidTest`). */
class CoreFlowsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun failedJobShowsReasonAndRetries() {
        var retried = 0
        val job = PrintJob(1, 1, "Test print", JobStatus.FAILED, 3, "Printer is out of paper. Load a new roll, then tap Retry.")
        rule.setContent { PosPrintTheme { JobRow(job, onRetry = { retried++ }, onCancel = {}) } }
        rule.onNodeWithText("Printer is out of paper. Load a new roll, then tap Retry.").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
    }

    @Test fun queuedJobCanBeCancelled() {
        var cancelled = 0
        rule.setContent { PosPrintTheme { JobRow(PrintJob(2, 1, "Receipt"), onRetry = {}, onCancel = { cancelled++ }) } }
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test fun connectionChipIsAccessible() {
        rule.setContent { PosPrintTheme { ConnectionChip(ConnectionState.CONNECTED) } }
        rule.onNodeWithContentDescription("Printer status: Connected").assertIsDisplayed()
    }

    @Test fun emptyStateShowsGuidance() {
        rule.setContent { PosPrintTheme { EmptyState(Icons.Rounded.Print, "No printers saved", "Add one") } }
        rule.onNodeWithText("No printers saved").assertIsDisplayed()
    }

    /** Regression: Android's regex engine (ICU) is stricter than the JVM's, so engines must be exercised on a device. */
    @Test fun templateAndLabelEnginesInitialiseOnAndroid() {
        val starters = com.posprint.app.data.StarterTemplates.all()
        val receipt = starters.first { it.kind == com.posprint.app.domain.template.TemplateKind.RECEIPT }
        val out = com.posprint.app.domain.render.TemplateEngine.resolve(
            receipt.blocks, receipt.sample, com.posprint.app.domain.render.RenderContext(32),
        )
        assert(out.isNotEmpty())
        val label = starters.first { it.kind == com.posprint.app.domain.template.TemplateKind.LABEL }
        assert(com.posprint.app.domain.render.LabelPlanner.variablesUsed(label.elements).isNotEmpty())
    }

    @Test fun stepperRespectsBounds() {
        var value = 1
        rule.setContent { PosPrintTheme { Stepper("Copies", value, 1..2) { value = it } } }
        rule.onNodeWithContentDescription("Decrease Copies").assertIsDisplayed()
        rule.onNodeWithContentDescription("Increase Copies").performClick()
        assertEquals(2, value)
    }
}
