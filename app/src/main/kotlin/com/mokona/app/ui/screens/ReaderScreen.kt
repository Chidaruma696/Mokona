package com.mokona.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
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
import coil3.compose.AsyncImage
import com.mokona.app.R
import com.mokona.app.data.Illust
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
	Box(Modifier.fillMaxSize().background(Color.Black)) {
		if (horizontal) {
			val pager = rememberPagerState(initialPage = current) { pages.size }
			current = pager.currentPage
			// reverseLayout puts page 1 on the right, so swiping leftwards goes back: manga order.
			HorizontalPager(state = pager, reverseLayout = true, modifier = Modifier.fillMaxSize(), key = { pages[it] }) { page ->
				ReaderPage(pages[page], fit = true)
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
					AsyncImage(
						model = url,
						contentDescription = "${index + 1}",
						contentScale = ContentScale.FillWidth,
						modifier = Modifier.fillMaxWidth().clickable { onView(pages, index) },
					)
				}
			}
		}
		Box(Modifier.safeDrawingPadding().fillMaxSize()) {
			IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
				Icon(Icons.Default.Close, contentDescription = stringResource(R.string.back), tint = Color.White)
			}
			IconButton(
				onClick = { horizontal = !horizontal; AppPrefs.updateReaderHorizontal(horizontal) },
				modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
			) {
				Icon(if (horizontal) Icons.Default.List else Icons.Default.PlayArrow, contentDescription = stringResource(R.string.reader_mode), tint = Color.White)
			}
			Text("${current + 1}/${pages.size}", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
		}
	}
}

@Composable
private fun ReaderPage(url: String, fit: Boolean) {
	var scale by remember { mutableFloatStateOf(1f) }
	var offset by remember { mutableStateOf(Offset.Zero) }
	val state = rememberTransformableState { zoom, pan, _ ->
		scale = (scale * zoom).coerceIn(1f, 5f)
		offset = if (scale > 1f) offset + pan else Offset.Zero
	}
	AsyncImage(
		model = url,
		contentDescription = null,
		contentScale = if (fit) ContentScale.Fit else ContentScale.FillWidth,
		modifier = Modifier.fillMaxSize().transformable(state).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
	)
}
