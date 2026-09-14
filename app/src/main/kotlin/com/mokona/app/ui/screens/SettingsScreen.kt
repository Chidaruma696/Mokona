package com.mokona.app.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mokona.app.BuildConfig
import com.mokona.app.R
import com.mokona.app.data.PixivAuth
import com.mokona.app.ui.AppPrefs
import com.mokona.app.ui.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onLogin: () -> Unit) {
	val context = LocalContext.current
	Column(Modifier.fillMaxSize()) {
		TopAppBar(title = { Text(stringResource(R.string.settings)) })
		Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			SectionTitle(stringResource(R.string.account))
			Card(Modifier.padding(horizontal = 12.dp)) {
				if (PixivAuth.isLoggedIn) {
					ListItem(
						headlineContent = { Text(PixivAuth.userName ?: "") },
						supportingContent = { Text(stringResource(R.string.logged_in)) },
						trailingContent = { TextButton(onClick = { PixivAuth.logout() }) { Text(stringResource(R.string.logout)) } },
					)
				} else {
					ListItem(
						headlineContent = { Text(stringResource(R.string.not_logged_in)) },
						supportingContent = { Text(stringResource(R.string.login_needed_body)) },
						trailingContent = { Button(onClick = onLogin) { Text(stringResource(R.string.login)) } },
					)
				}
			}

			SectionTitle(stringResource(R.string.appearance))
			Card(Modifier.padding(horizontal = 12.dp)) {
				ListItem(
					headlineContent = { Text(stringResource(R.string.theme_mode)) },
					supportingContent = {
						Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
							FilterChip(selected = AppPrefs.themeMode == ThemeMode.SYSTEM, onClick = { AppPrefs.updateThemeMode(ThemeMode.SYSTEM) }, label = { Text(stringResource(R.string.mode_system)) })
							FilterChip(selected = AppPrefs.themeMode == ThemeMode.LIGHT, onClick = { AppPrefs.updateThemeMode(ThemeMode.LIGHT) }, label = { Text(stringResource(R.string.mode_light)) })
							FilterChip(selected = AppPrefs.themeMode == ThemeMode.DARK, onClick = { AppPrefs.updateThemeMode(ThemeMode.DARK) }, label = { Text(stringResource(R.string.mode_dark)) })
						}
					},
				)
				ListItem(
					headlineContent = { Text(stringResource(R.string.amoled)) },
					supportingContent = { Text(stringResource(R.string.amoled_summary)) },
					trailingContent = { Switch(checked = AppPrefs.amoled, onCheckedChange = { AppPrefs.updateAmoled(it) }) },
				)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					ListItem(
						headlineContent = { Text(stringResource(R.string.dynamic_color)) },
						supportingContent = { Text(stringResource(R.string.dynamic_color_summary)) },
						trailingContent = { Switch(checked = AppPrefs.dynamicColor, onCheckedChange = { AppPrefs.updateDynamicColor(it) }) },
					)
				}
				ListItem(
					headlineContent = { Text(stringResource(R.string.grid_columns)) },
					supportingContent = {
						Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
							for (n in 1..4) FilterChip(selected = AppPrefs.gridColumns == n, onClick = { AppPrefs.updateGridColumns(n) }, label = { Text("$n") })
						}
					},
				)
			}

			SectionTitle(stringResource(R.string.content))
			Card(Modifier.padding(horizontal = 12.dp)) {
				ListItem(
					headlineContent = { Text(stringResource(R.string.show_adult)) },
					supportingContent = { Text(stringResource(R.string.show_adult_summary)) },
					trailingContent = { Switch(checked = AppPrefs.showAdult, onCheckedChange = { AppPrefs.updateShowAdult(it) }) },
				)
			}

			SectionTitle(stringResource(R.string.about))
			AboutSection()
			Text(
				"Mokona ${BuildConfig.VERSION_NAME}",
				style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.fillMaxWidth().padding(16.dp),
			)
		}
	}
	// keeps the context import honest when the About rows need it
	context.hashCode()
}

@Composable
fun SectionTitle(text: String) {
	Text(
		text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
	)
}
