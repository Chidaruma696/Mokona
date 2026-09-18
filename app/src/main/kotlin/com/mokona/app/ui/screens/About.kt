package com.mokona.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mokona.app.BuildConfig
import com.mokona.app.R
import com.mokona.app.data.Updates
import com.mokona.app.ui.AppPrefs

const val KEEP_ANDROID_OPEN_URL = "https://keepandroidopen.org/es/"
const val REPO_URL = "https://github.com/Chidaruma696/Mokona"
private const val NOTICES_URL = "$REPO_URL/blob/main/THIRD_PARTY_NOTICES.md"
private const val PIXIV_URL = "https://www.pixiv.net/"
private const val PIXIVPY_URL = "https://github.com/upbit/pixivpy"
private const val MLKIT_URL = "https://developers.google.com/ml-kit"

/**
 * Keep Android Open notice. Google's developer verification (announced 2025, enforced from 2027) blocks apps from
 * unregistered developers on every certified device, Play Store or not; Mokona cannot exist under that rule.
 */
@Composable
fun KeepAndroidOpenBanner(modifier: Modifier = Modifier) {
	val context = LocalContext.current
	Card(
		modifier = modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
	) {
		Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text(stringResource(R.string.kao_kicker), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
			Text(stringResource(R.string.kao_title), style = MaterialTheme.typography.titleMedium)
			Text(stringResource(R.string.kao_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
				Button(onClick = { context.openUrl(KEEP_ANDROID_OPEN_URL) }) { Text(stringResource(R.string.kao_learn)) }
				TextButton(onClick = { AppPrefs.updateKaoBanner(false) }) { Text(stringResource(R.string.kao_hide)) }
			}
		}
	}
}

/** New version on GitHub: shown on Home until downloaded, dismissed or skipped. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
	val update = Updates.available ?: return
	val context = LocalContext.current
	Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
		Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text(stringResource(R.string.update_available, update.version), style = MaterialTheme.typography.titleMedium)
			Text(stringResource(R.string.update_body, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
				Button(onClick = { context.openUrl(update.apkUrl ?: update.pageUrl) }) { Text(stringResource(R.string.update_download)) }
				TextButton(onClick = { Updates.skip(update.version) }) { Text(stringResource(R.string.update_skip)) }
				TextButton(onClick = { Updates.dismiss() }) { Text(stringResource(R.string.kao_hide)) }
			}
		}
	}
}

/** Settings › About: Keep Android Open first, then credits and licenses. */
@Composable
fun AboutSection() {
	val context = LocalContext.current
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Card(Modifier.padding(horizontal = 12.dp)) {
			ListItem(
				headlineContent = { Text("Keep Android Open") },
				supportingContent = { Text(stringResource(R.string.kao_row_summary)) },
				modifier = Modifier.padding(0.dp),
				trailingContent = { TextButton(onClick = { context.openUrl(KEEP_ANDROID_OPEN_URL) }) { Text(stringResource(R.string.kao_learn)) } },
			)
			ListItem(
				headlineContent = { Text(stringResource(R.string.kao_show_banner)) },
				trailingContent = { Switch(checked = AppPrefs.kaoBannerVisible, onCheckedChange = { AppPrefs.updateKaoBanner(it) }) },
			)
		}
		Card(Modifier.padding(horizontal = 12.dp)) {
			ListItem(
				headlineContent = { Text(stringResource(R.string.credit_pixiv)) },
				supportingContent = { Text(stringResource(R.string.credit_pixiv_summary)) },
				trailingContent = { TextButton(onClick = { context.openUrl(PIXIV_URL) }) { Text("pixiv.net") } },
			)
			ListItem(
				headlineContent = { Text(stringResource(R.string.credit_api)) },
				supportingContent = { Text(stringResource(R.string.credit_api_summary)) },
				trailingContent = { TextButton(onClick = { context.openUrl(PIXIVPY_URL) }) { Text("pixivpy") } },
			)
			ListItem(
				headlineContent = { Text(stringResource(R.string.credit_mlkit)) },
				supportingContent = { Text(stringResource(R.string.credit_mlkit_summary)) },
				trailingContent = { TextButton(onClick = { context.openUrl(MLKIT_URL) }) { Text("ML Kit") } },
			)
			ListItem(
				headlineContent = { Text(stringResource(R.string.credit_name)) },
				supportingContent = { Text(stringResource(R.string.credit_name_summary)) },
			)
			ListItem(
				headlineContent = { Text(stringResource(R.string.credit_licenses)) },
				supportingContent = { Text(stringResource(R.string.credit_licenses_summary)) },
				trailingContent = { TextButton(onClick = { context.openUrl(NOTICES_URL) }) { Text("→") } },
			)
			ListItem(
				headlineContent = { Text(stringResource(R.string.credit_source_code)) },
				supportingContent = { Text(stringResource(R.string.credit_source_code_summary)) },
				trailingContent = { TextButton(onClick = { context.openUrl(REPO_URL) }) { Text("GitHub") } },
			)
		}
	}
}
