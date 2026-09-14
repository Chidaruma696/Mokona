package com.mokona.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mokona.app.R
import com.mokona.app.ui.LoginViewModel

/**
 * Pixiv's own login page inside a WebView. When it finishes it redirects to
 * pixiv://account/login?code=…; that code plus our PKCE verifier become the tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onBack: () -> Unit, onDone: () -> Unit, vm: LoginViewModel = viewModel()) {
	val startUrl = remember { vm.startUrl() }
	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(stringResource(R.string.login)) },
			navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
		)
		Box(Modifier.fillMaxSize()) {
			AndroidView(
				modifier = Modifier.fillMaxSize(),
				factory = { context ->
					WebView(context).apply {
						settings.javaScriptEnabled = true
						settings.domStorageEnabled = true
						CookieManager.getInstance().setAcceptCookie(true)
						webViewClient = object : WebViewClient() {
							override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
								val url = request.url
								if (url.scheme == "pixiv") {
									val code = url.getQueryParameter("code")
									if (code != null) vm.finish(code, onDone)
									return true
								}
								return false
							}
						}
						loadUrl(startUrl)
					}
				},
			)
			if (vm.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
			vm.error?.let { Text(it, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) }
		}
	}
}
