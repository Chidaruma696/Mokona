package com.mokona.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.mokona.app.R
import com.mokona.app.data.Illust
import com.mokona.app.data.PageTranslator
import com.mokona.app.ui.AppPrefs

/**
 * Comic reader for multi-page works. Vertical mode: one long strip, every page at full
 * width in its real proportion, pinch to zoom. Horizontal mode: one page at a time,
 * right to left like a Japanese manga. Tap a page in vertical mode to open it full screen.
 */
@Composable
fun ReaderScreen(illust: Illust, onClose: () -> Unit, onView: (List<String>, Int) -> Unit) {
	val pages = illust.largeUrls
	var horizontal by remember { mutableStateOf(AppPrefs.readerHorizontal) }
	var current by remember { mutableStateOf(0) }
	// Manga in your language: the overlay reads and translates the pages on screen, one ahead.
	val context = LocalContext.current
	var translating by remember { mutableStateOf(false) }
	var status by remember { mutableStateOf<PageTranslator.Status>(PageTranslator.Status.Idle) }
	val translations = remember { mutableStateMapOf<String, PageTranslator.Result>() }
	LaunchedEffect(translating, current) {
		if (!translating) return@LaunchedEffect
		for (i in listOf(current, current + 1)) {
			val url = pages.getOrNull(i) ?: continue
			if (translations.containsKey(url)) continue
			runCatching { translations[url] = PageTranslator.translate(context, url) { status = it } }
				.onFailure { status = PageTranslator.Status.Failed(it.message ?: it.javaClass.simpleName) }
		}
	}
	fun translationOf(url: String) = if (translating) translations[url] else null
	// Which note is highlighted, per page (url → index).
	var selected by remember { mutableStateOf<Pair<String, Int>?>(null) }
	fun pick(url: String, i: Int) { selected = if (selected == url to i) null else url to i }
	Box(Modifier.fillMaxSize().background(Color.Black)) {
		if (horizontal) {
			val pager = rememberPagerState(initialPage = current) { pages.size }
			current = pager.currentPage
			// reverseLayout puts page 1 on the right, so swiping leftwards goes back: manga order.
			HorizontalPager(state = pager, reverseLayout = true, modifier = Modifier.fillMaxSize(), key = { pages[it] }) { page ->
				val url = pages[page]
				ReaderPage(url, fit = true, translation = translationOf(url), selected = selected?.takeIf { it.first == url }?.second, onSelect = { pick(url, it) })
			}
			val t = translationOf(pages.getOrNull(current) ?: "")
			if (t != null && AppPrefs.translationNotes) {
				val url = pages[current]
				TranslationNotesSheet(t, selected?.takeIf { it.first == url }?.second, { pick(url, it) }, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
			}
		} else {
			var scale by remember { mutableFloatStateOf(1f) }
			var offset by remember { mutableStateOf(Offset.Zero) }
			val zoom = rememberTransformableState { z, pan, _ ->
				scale = (scale * z).coerceIn(1f, 4f)
				offset = if (scale > 1f) offset + pan else Offset.Zero
			}
			val list = rememberLazyListState(initialFirstVisibleItemIndex = current)
			current = list.firstVisibleItemIndex
			LazyColumn(
				state = list,
				modifier = Modifier.fillMaxSize().transformable(zoom).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
			) {
				itemsIndexed(pages, key = { _, u -> u }) { index, url ->
					val t = translationOf(url)
					if (t != null) {
						val sel = selected?.takeIf { it.first == url }?.second
						TranslatablePage(url, t, ContentScale.FillWidth, Modifier.fillMaxWidth().aspectRatio(t.width.toFloat() / t.height.coerceAtLeast(1)), selected = sel, onSelect = { pick(url, it) })
						// In the strip the notes sit right under their page: the manga reads like a translated script.
						if (AppPrefs.translationNotes) TranslationNotes(t, sel, { pick(url, it) }, Modifier.background(Color(0xFF181818)).padding(vertical = 4.dp), dark = true)
					} else {
						AsyncImage(
							model = url,
							contentDescription = "${index + 1}",
							contentScale = ContentScale.FillWidth,
							modifier = Modifier.fillMaxWidth().clickable { onView(pages, index) },
						)
					}
				}
			}
		}
		Box(Modifier.safeDrawingPadding().fillMaxSize()) {
			IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
				Icon(Icons.Default.Close, contentDescription = stringResource(R.string.back), tint = Color.White)
			}
			Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
				IconButton(onClick = { translating = !translating; if (!translating) status = PageTranslator.Status.Idle }) {
					Text("訳", color = if (translating) Color(0xFFFFD54F) else Color.White, style = MaterialTheme.typography.titleMedium)
				}
				IconButton(onClick = { horizontal = !horizontal; AppPrefs.updateReaderHorizontal(horizontal) }) {
					Icon(if (horizontal) Icons.Default.List else Icons.Default.PlayArrow, contentDescription = stringResource(R.string.reader_mode), tint = Color.White)
				}
			}
			if (translating) {
				TranslationBar(translationOf(pages.getOrNull(current) ?: "")?.source, status = status, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 40.dp))
			}
			Text("${current + 1}/${pages.size}", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
		}
	}
}

@Composable
private fun ReaderPage(url: String, fit: Boolean, translation: PageTranslator.Result? = null, selected: Int? = null, onSelect: (Int) -> Unit = {}) {
	var scale by remember { mutableFloatStateOf(1f) }
	var offset by remember { mutableStateOf(Offset.Zero) }
	val state = rememberTransformableState { zoom, pan, _ ->
		scale = (scale * zoom).coerceIn(1f, 5f)
		offset = if (scale > 1f) offset + pan else Offset.Zero
	}
	TranslatablePage(
		url, translation, if (fit) ContentScale.Fit else ContentScale.FillWidth,
		Modifier.fillMaxSize().transformable(state).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
		selected = selected, onSelect = onSelect,
	)
}
