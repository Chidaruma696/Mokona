package com.mokona.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.mokona.app.MokonaApp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide switches, observable from Compose. */
object AppPrefs {
	private const val FILE = "app"

	var onboardingDone by mutableStateOf(false)
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

	fun init(context: Context) {
		val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		onboardingDone = p.getBoolean("onboarding_done", false)
		showAdult = p.getBoolean("show_adult", false)
		themeMode = runCatching { ThemeMode.valueOf(p.getString("theme_mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
		amoled = p.getBoolean("amoled", false)
		dynamicColor = p.getBoolean("dynamic_color", true)
		gridColumns = p.getInt("grid_columns", 2)
		kaoBannerVisible = p.getBoolean("kao_banner", true)
		readerHorizontal = p.getBoolean("reader_horizontal", false)
	}

	private fun prefs() = MokonaApp.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	fun setOnboardingDone() { onboardingDone = true; prefs().edit { putBoolean("onboarding_done", true) } }
	fun updateShowAdult(value: Boolean) { showAdult = value; prefs().edit { putBoolean("show_adult", value) } }
	fun updateThemeMode(value: ThemeMode) { themeMode = value; prefs().edit { putString("theme_mode", value.name) } }
	fun updateAmoled(value: Boolean) { amoled = value; prefs().edit { putBoolean("amoled", value) } }
	fun updateDynamicColor(value: Boolean) { dynamicColor = value; prefs().edit { putBoolean("dynamic_color", value) } }
	fun updateGridColumns(value: Int) { gridColumns = value.coerceIn(1, 4); prefs().edit { putInt("grid_columns", gridColumns) } }
	fun updateReaderHorizontal(value: Boolean) { readerHorizontal = value; prefs().edit { putBoolean("reader_horizontal", value) } }
	fun updateKaoBanner(value: Boolean) { kaoBannerVisible = value; prefs().edit { putBoolean("kao_banner", value) } }
}
