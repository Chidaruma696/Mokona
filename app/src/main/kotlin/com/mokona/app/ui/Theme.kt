package com.mokona.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette for Android 11 and older, or when dynamic color is off: Mokona's black with a red jewel.
private val LightScheme = lightColorScheme(
	primary = Color(0xFFB3261E), onPrimary = Color.White,
	secondary = Color(0xFF5D5F66), tertiary = Color(0xFF7D5260),
)
private val DarkScheme = darkColorScheme(
	primary = Color(0xFFFFB4AB), onPrimary = Color(0xFF690005),
	secondary = Color(0xFFC6C6CE), tertiary = Color(0xFFEFB8C8),
)

/** Pure black surfaces on top of any dark scheme: what an OLED screen turns off. */
private fun ColorScheme.amoled(): ColorScheme = copy(
	background = Color.Black,
	surface = Color.Black,
	surfaceContainerLowest = Color.Black,
	surfaceContainerLow = Color(0xFF0A0A0A),
	surfaceContainer = Color(0xFF111111),
	surfaceContainerHigh = Color(0xFF1A1A1A),
	surfaceContainerHighest = Color(0xFF222222),
	surfaceVariant = Color(0xFF1A1A1A),
)

@Composable
fun isDarkTheme(): Boolean = when (AppPrefs.themeMode) {
	ThemeMode.SYSTEM -> isSystemInDarkTheme()
	ThemeMode.LIGHT -> false
	ThemeMode.DARK -> true
}

@Composable
fun MokonaTheme(content: @Composable () -> Unit) {
	val dark = isDarkTheme()
	val context = LocalContext.current
	val dynamic = AppPrefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
	var scheme = when {
		dynamic && dark -> dynamicDarkColorScheme(context)
		dynamic -> dynamicLightColorScheme(context)
		dark -> DarkScheme
		else -> LightScheme
	}
	if (dark && AppPrefs.amoled) scheme = scheme.amoled()
	MaterialTheme(colorScheme = scheme, content = content)
}
