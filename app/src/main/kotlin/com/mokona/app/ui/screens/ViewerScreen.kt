package com.mokona.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import com.mokona.app.R
import com.mokona.app.data.PageTranslator
import com.mokona.app.ui.AppPrefs

/** Full-screen pages on black: swipe between pages, pinch to zoom, double tap to toggle 2.5x. */
@Composable
fun ViewerScreen(urls: List<String>, startIndex: Int, onClose: () -> Unit) {
	val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) { urls.size }
	val context = LocalContext.current
	var translating by remember { mutableStateOf(false) }
	var source by remember { mutableStateOf(AppPrefs.ocrSource) }
	var status by remember { mutableStateOf<PageTranslator.Status>(PageTranslator.Status.Idle) }
	val translations = remember { mutableStateMapOf<String, PageTranslator.Result>() }
	LaunchedEffect(translating, pager.currentPage, source) {
		if (!translating) return@LaunchedEffect
		val url = urls.getOrNull(pager.currentPage) ?: return@LaunchedEffect
		if (translations.containsKey("$source:$url")) return@LaunchedEffect
		runCatching { translations["$source:$url"] = PageTranslator.translate(context, url, source) { status = it } }
			.onFailure { status = PageTranslator.Status.Failed(it.message ?: it.javaClass.simpleName) }
	}
	var selected by remember { mutableStateOf<Pair<String, Int>?>(null) }
	fun pick(url: String, i: Int) { selected = if (selected == url to i) null else url to i }
	Box(Modifier.fillMaxSize().background(Color.Black)) {
		HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { urls[it] }) { page ->
			val url = urls[page]
			ZoomableImage(url, if (translating) translations["$source:$url"] else null, selected?.takeIf { it.first == url }?.second) { pick(url, it) }
		}
		val currentUrl = urls.getOrNull(pager.currentPage)
		val t = if (translating && currentUrl != null) translations["$source:$currentUrl"] else null
		if (t != null && currentUrl != null && AppPrefs.translationNotes) {
			TranslationNotesSheet(t, selected?.takeIf { it.first == currentUrl }?.second, { pick(currentUrl, it) }, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
		}
		Box(Modifier.safeDrawingPadding().fillMaxSize()) {
			IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
				Icon(Icons.Default.Close, contentDescription = stringResource(R.string.back), tint = Color.White)
			}
			Row(Modifier.align(Alignment.TopEnd).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
				if (urls.size > 1) Text("${pager.currentPage + 1}/${urls.size}", color = Color.White, modifier = Modifier.padding(end = 8.dp))
				IconButton(onClick = { translating = !translating; if (!translating) status = PageTranslator.Status.Idle }) {
					Text("訳", color = if (translating) Color(0xFFFFD54F) else Color.White, style = MaterialTheme.typography.titleMedium)
				}
			}
			if (translating) {
				TranslationBar(source, onSource = { source = it; AppPrefs.updateOcrSource(it) }, status = status, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp))
			}
		}
	}
}

@Composable
private fun ZoomableImage(url: String, translation: PageTranslator.Result? = null, selected: Int? = null, onSelect: (Int) -> Unit = {}) {
	var scale by remember { mutableFloatStateOf(1f) }
	var offset by remember { mutableStateOf(Offset.Zero) }
	val state = rememberTransformableState { zoom, pan, _ ->
		scale = (scale * zoom).coerceIn(1f, 6f)
		offset = if (scale > 1f) offset + pan else Offset.Zero
	}
	TranslatablePage(
		url, translation, ContentScale.Fit,
		Modifier
			.fillMaxSize()
			.pointerInput(Unit) {
				detectTapGestures(onDoubleTap = {
					if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
				})
			}
			.transformable(state)
			.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
		selected = selected, onSelect = onSelect,
	)
}
