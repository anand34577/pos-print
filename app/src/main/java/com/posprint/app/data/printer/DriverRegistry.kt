package com.posprint.app.data.printer

import com.posprint.app.data.printer.escpos.EscPosDriver
import com.posprint.app.data.printer.label.CpclDriver
import com.posprint.app.data.printer.label.EplDriver
import com.posprint.app.data.printer.label.LabelDriver
import com.posprint.app.data.printer.label.TsplDriver
import com.posprint.app.data.printer.label.ZplDriver
import com.posprint.app.domain.model.PrinterLanguage
import com.posprint.app.domain.printer.PrinterDriver
import javax.inject.Inject
import javax.inject.Singleton

/** One place to register drivers. To support a new language, add a driver here and a value to [PrinterLanguage]. */
@Singleton
class DriverRegistry @Inject constructor() {
    private val drivers: Map<PrinterLanguage, PrinterDriver> =
        listOf(EscPosDriver(), TsplDriver(), ZplDriver(), EplDriver(), CpclDriver()).associateBy { it.language }

    fun forLanguage(language: PrinterLanguage): PrinterDriver =
        drivers[language] ?: error("No driver registered for $language")

    fun labelDriver(language: PrinterLanguage): LabelDriver? = drivers[language] as? LabelDriver
}
