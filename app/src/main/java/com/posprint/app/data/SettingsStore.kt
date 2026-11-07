package com.posprint.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.posprint.app.data.settings.BusinessProfile
import com.posprint.app.data.settings.DirectionMode
import com.posprint.app.data.settings.ThemeMode
import com.posprint.app.data.settings.UiPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val name = stringPreferencesKey("biz_name")
        val address = stringPreferencesKey("biz_address")
        val phone = stringPreferencesKey("biz_phone")
        val taxId = stringPreferencesKey("biz_tax")
        val footer = stringPreferencesKey("biz_footer")
        val logo = booleanPreferencesKey("biz_logo")
        val currency = stringPreferencesKey("fmt_currency")
        val currencyAfter = booleanPreferencesKey("fmt_currency_after")
        val decimals = intPreferencesKey("fmt_decimals")
        val thousands = booleanPreferencesKey("fmt_thousands")
        val datePattern = stringPreferencesKey("fmt_date")
        val timePattern = stringPreferencesKey("fmt_time")
        val taxPct = stringPreferencesKey("biz_tax_pct")
        val theme = stringPreferencesKey("ui_theme")
        val dynamic = booleanPreferencesKey("ui_dynamic")
        val direction = stringPreferencesKey("ui_direction")
        val margin = intPreferencesKey("paper_margin")
        val scale = intPreferencesKey("paper_scale")
    }

    val logoFile: File get() = File(context.filesDir, "logo.png")

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[K.onboarding] ?: false }
    suspend fun setOnboardingDone() { context.dataStore.edit { it[K.onboarding] = true } }

    val profile: Flow<BusinessProfile> = context.dataStore.data.map { p ->
        val d = BusinessProfile()
        BusinessProfile(
            name = p[K.name] ?: d.name, address = p[K.address] ?: d.address, phone = p[K.phone] ?: d.phone,
            taxId = p[K.taxId] ?: d.taxId, footer = p[K.footer] ?: d.footer,
            hasLogo = (p[K.logo] ?: false) && logoFile.exists(),
            currency = p[K.currency] ?: d.currency, currencyAfter = p[K.currencyAfter] ?: d.currencyAfter,
            decimals = p[K.decimals] ?: d.decimals, thousands = p[K.thousands] ?: d.thousands,
            datePattern = p[K.datePattern] ?: d.datePattern, timePattern = p[K.timePattern] ?: d.timePattern,
            defaultTaxPct = p[K.taxPct] ?: d.defaultTaxPct,
        )
    }

    suspend fun saveProfile(b: BusinessProfile) {
        context.dataStore.edit {
            it[K.name] = b.name; it[K.address] = b.address; it[K.phone] = b.phone; it[K.taxId] = b.taxId
            it[K.footer] = b.footer; it[K.currency] = b.currency; it[K.currencyAfter] = b.currencyAfter
            it[K.decimals] = b.decimals; it[K.thousands] = b.thousands; it[K.datePattern] = b.datePattern
            it[K.timePattern] = b.timePattern; it[K.taxPct] = b.defaultTaxPct
        }
    }

    /** Saves the picked image as the business logo, scaled to at most 576 px wide (80 mm at 203 dpi). */
    suspend fun setLogo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bmp = runCatching {
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return@withContext false
        val scale = minOf(1f, 576f / bmp.width)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        logoFile.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        context.dataStore.edit { it[K.logo] = true }
        true
    }

    suspend fun setLogoBytes(png: ByteArray) {
        withContext(Dispatchers.IO) { logoFile.writeBytes(png) }
        context.dataStore.edit { it[K.logo] = true }
    }

    suspend fun clearLogo() {
        logoFile.delete()
        context.dataStore.edit { it[K.logo] = false }
    }

    val ui: Flow<UiPrefs> = context.dataStore.data.map { p: Preferences ->
        val d = UiPrefs()
        UiPrefs(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            dynamicColor = p[K.dynamic] ?: d.dynamicColor,
            direction = p[K.direction]?.let { runCatching { DirectionMode.valueOf(it) }.getOrNull() } ?: d.direction,
            onboardingDone = p[K.onboarding] ?: false,
            paperMarginMm = p[K.margin] ?: d.paperMarginMm,
            paperScalePct = p[K.scale] ?: d.paperScalePct,
        )
    }

    suspend fun saveUi(u: UiPrefs) {
        context.dataStore.edit {
            it[K.theme] = u.theme.name; it[K.dynamic] = u.dynamicColor; it[K.direction] = u.direction.name
            it[K.margin] = u.paperMarginMm; it[K.scale] = u.paperScalePct
        }
    }

    suspend fun profileNow() = profile.first()

    /** The number the next receipt will get, without using it up (to prefill forms). */
    suspend fun peekReceiptNumber(): Int = (context.dataStore.data.first()[intPreferencesKey("receipt_no")] ?: 1000) + 1

    /** Increments and returns the receipt counter used by Mini POS and receipt templates. */
    suspend fun nextReceiptNumber(): Int {
        val key = intPreferencesKey("receipt_no")
        var next = 1
        context.dataStore.edit { next = (it[key] ?: 1000) + 1; it[key] = next }
        return next
    }

    val seeded: Flow<Boolean> = context.dataStore.data.map { it[booleanPreferencesKey("templates_seeded")] ?: false }
    suspend fun markSeeded() { context.dataStore.edit { it[booleanPreferencesKey("templates_seeded")] = true } }
}
