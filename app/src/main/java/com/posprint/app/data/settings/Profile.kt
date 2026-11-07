package com.posprint.app.data.settings

import com.posprint.app.domain.render.BusinessInfo
import com.posprint.app.domain.render.Formatter

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class DirectionMode { AUTO, LTR, RTL }

@kotlinx.serialization.Serializable
data class BusinessProfile(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val taxId: String = "",
    val footer: String = "Thank you! Please come again.",
    val hasLogo: Boolean = false,
    val currency: String = "$",
    val currencyAfter: Boolean = false,
    val decimals: Int = 2,
    val thousands: Boolean = true,
    val datePattern: String = "yyyy-MM-dd",
    val timePattern: String = "HH:mm",
    val defaultTaxPct: String = "0",
) {
    fun business() = BusinessInfo(name, address, phone, taxId, footer, hasLogo)
    fun formatter() = Formatter(currency, decimals, thousands, datePattern, timePattern, currencyAfter)
}

@kotlinx.serialization.Serializable
data class UiPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val direction: DirectionMode = DirectionMode.AUTO,
    val onboardingDone: Boolean = false,
    val paperMarginMm: Int = 12,
    val paperScalePct: Int = 100,
)
