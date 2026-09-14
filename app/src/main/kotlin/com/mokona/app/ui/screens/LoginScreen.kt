package com.mokona.app.ui.screens

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mokona.app.R
import com.mokona.app.ui.LoginBridge
import com.mokona.app.ui.LoginViewModel

/**
 * Sign-in happens in the device browser (a Chrome Custom Tab), never inside the app:
 * the password is typed on pixiv.net with the browser's own protections. When Pixiv is
 * done it opens pixiv://account/login?code=..., which lands in MainActivity and here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onBack: () -> Unit, onDone: () -> Unit, vm: LoginViewModel = viewModel()) {
	val context = LocalContext.current
	var manualCode by remember { mutableStateOf("") }

	val pending = LoginBridge.pendingCode
	LaunchedEffect(pending) {
		if (pending != null) {
			LoginBridge.pendingCode = null
			vm.finish(pending, onDone)
		}
	}

	fun openBrowser() {
		val url = vm.startUrl()
		runCatching {
			CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
		}.onFailure { context.openUrl(url) }
	}

	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(stringResource(R.string.login)) },
			navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
		)
		Column(
			Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Card {
				Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
					Text(stringResource(R.string.login_browser_title), style = MaterialTheme.typography.titleLarge)
					Text(stringResource(R.string.login_browser_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Button(onClick = { openBrowser() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
						Text(stringResource(R.string.login_open_browser))
					}
				}
			}
			if (vm.busy) CircularProgressIndicator(Modifier.padding(8.dp))
			vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

			Text(stringResource(R.string.login_manual_title), style = MaterialTheme.typography.titleSmall)
			Text(stringResource(R.string.login_manual_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			OutlinedTextField(
				value = manualCode,
				onValueChange = { manualCode = it },
				modifier = Modifier.fillMaxWidth(),
				singleLine = true,
				placeholder = { Text("code") },
			)
			TextButton(onClick = { extractCode(manualCode)?.let { vm.finish(it, onDone) } }, enabled = manualCode.isNotBlank() && !vm.busy) {
				Text(stringResource(R.string.login_manual_use))
			}
		}
	}
}

/** Accepts either the bare code or the whole pixiv://account/login?code=... link. */
internal fun extractCode(text: String): String? {
	val t = text.trim()
	if (t.isEmpty()) return null
	return if (t.contains("code=")) t.substringAfter("code=").substringBefore('&').substringBefore('#').ifBlank { null } else t
}
