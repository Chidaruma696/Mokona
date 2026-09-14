package com.mokona.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mokona.app.R
import com.mokona.app.ui.AppPrefs

/** First start: what Mokona is, the adult filter (off by default), the OLED switch, then log in. */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
	Column(
		Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Text(stringResource(R.string.onboarding_kicker), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
		Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
		Card {
			Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
				Text(stringResource(R.string.onboarding_language_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
				LanguageChips()
			}
		}
		Text(stringResource(R.string.onboarding_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
		Card {
			ListItem(
				headlineContent = { Text(stringResource(R.string.show_adult)) },
				supportingContent = { Text(stringResource(R.string.show_adult_summary)) },
				trailingContent = { Switch(checked = AppPrefs.showAdult, onCheckedChange = { AppPrefs.updateShowAdult(it) }) },
			)
			ListItem(
				headlineContent = { Text(stringResource(R.string.amoled)) },
				supportingContent = { Text(stringResource(R.string.amoled_summary)) },
				trailingContent = { Switch(checked = AppPrefs.amoled, onCheckedChange = { AppPrefs.updateAmoled(it) }) },
			)
		}
		Text(stringResource(R.string.onboarding_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		Button(onClick = { AppPrefs.setOnboardingDone(); onStart() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start)) }
	}
}

/** English, Español, or the phone's language. Shared by the first start and Settings. */
@Composable
fun LanguageChips() {
	Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		FilterChip(selected = AppPrefs.language == "en", onClick = { AppPrefs.updateLanguage("en") }, label = { Text("English") })
		FilterChip(selected = AppPrefs.language == "es", onClick = { AppPrefs.updateLanguage("es") }, label = { Text("Español") })
		FilterChip(selected = AppPrefs.language == "system", onClick = { AppPrefs.updateLanguage("system") }, label = { Text(stringResource(R.string.lang_system)) })
	}
}
