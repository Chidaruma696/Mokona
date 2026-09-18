package com.mokona.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.mokona.app.MokonaApp
import com.mokona.app.data.AppLanguage
import com.mokona.app.data.PageTranslator

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide switches, observable from Compose. */
object AppPrefs {
	private const val FILE = "app"

	var onboardingDone by mutableStateOf(false)
		private set
	/** "en" (default), "es", or "system" to follow the phone. */
	var language by mutableStateOf("en")
		private set
	/** R-18 and R-18G works. Off by default; the switch lives in Settings › Content. */
	var showAdult by mutableStateOf(false)
		private set
	var themeMode by mutableStateOf(ThemeMode.SYSTEM)
		private set
	/** Pure black surfaces for OLED screens; only applies in dark mode. */
	var amoled by mutableStateOf(false)
		private set
	/** Material You colors taken from the wallpaper (Android 12+). */
	var dynamicColor by mutableStateOf(true)
		private set
	var gridColumns by mutableStateOf(2)
		private set
	/** Comic reader: one page at a time right to left (true) or a vertical strip (false). */
	var readerHorizontal by mutableStateOf(false)
		private set
	/** Keep Android Open notice on Home; hidden once the user dismisses it, can be shown again from Settings. */
	var kaoBannerVisible by mutableStateOf(true)
		private set
	/** Script the manga translator reads by default. */
	var ocrSource by mutableStateOf(PageTranslator.Source.JAPANESE)
		private set
	/** Translate with the offline dictionaries (downloaded in Settings) instead of online. Off by default. */
	var translateOffline by mutableStateOf(false)
		private set
	/** Size of the translated text, in sp. The box grows around it; the text never shrinks to fit. */
	var translationTextSize by mutableStateOf(14)
		private set

	fun init(context: Context) {
		val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		onboardingDone = p.getBoolean("onboarding_done", false)
		language = p.getString("language", "en") ?: "en"
		showAdult = p.getBoolean("show_adult", false)
		themeMode = runCatching { ThemeMode.valueOf(p.getString("theme_mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
		amoled = p.getBoolean("amoled", false)
		dynamicColor = p.getBoolean("dynamic_color", true)
		gridColumns = p.getInt("grid_columns", 2)
		kaoBannerVisible = p.getBoolean("kao_banner", true)
		readerHorizontal = p.getBoolean("reader_horizontal", false)
		ocrSource = runCatching { PageTranslator.Source.valueOf(p.getString("ocr_source", "JAPANESE")!!) }.getOrDefault(PageTranslator.Source.JAPANESE)
		translateOffline = p.getBoolean("translate_offline", false)
		translationTextSize = p.getInt("translation_text_size", 14)
		AppLanguage.current = language
		AppLanguage.offline = translateOffline
	}

	private fun prefs() = MokonaApp.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	fun updateLanguage(value: String) { language = value; AppLanguage.current = value; prefs().edit { putString("language", value) }; applyLanguage() }
	fun updateOcrSource(value: PageTranslator.Source) { ocrSource = value; prefs().edit { putString("ocr_source", value.name) } }
	fun updateTranslationTextSize(value: Int) { translationTextSize = value.coerceIn(8, 32); prefs().edit { putInt("translation_text_size", translationTextSize) } }
	fun updateTranslateOffline(value: Boolean) { translateOffline = value; AppLanguage.offline = value; prefs().edit { putBoolean("translate_offline", value) } }

	/** Makes the app speak the chosen language; recreates the activity when it changes. */
	fun applyLanguage() {
		val want = if (language == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language)
		if (AppCompatDelegate.getApplicationLocales() != want) AppCompatDelegate.setApplicationLocales(want)
	}

	fun setOnboardingDone() { onboardingDone = true; prefs().edit { putBoolean("onboarding_done", true) } }
	fun updateShowAdult(value: Boolean) { showAdult = value; prefs().edit { putBoolean("show_adult", value) } }
	fun updateThemeMode(value: ThemeMode) { themeMode = value; prefs().edit { putString("theme_mode", value.name) } }
	fun updateAmoled(value: Boolean) { amoled = value; prefs().edit { putBoolean("amoled", value) } }
	fun updateDynamicColor(value: Boolean) { dynamicColor = value; prefs().edit { putBoolean("dynamic_color", value) } }
	fun updateGridColumns(value: Int) { gridColumns = value.coerceIn(1, 4); prefs().edit { putInt("grid_columns", gridColumns) } }
	fun updateReaderHorizontal(value: Boolean) { readerHorizontal = value; prefs().edit { putBoolean("reader_horizontal", value) } }
	fun updateKaoBanner(value: Boolean) { kaoBannerVisible = value; prefs().edit { putBoolean("kao_banner", value) } }
}
