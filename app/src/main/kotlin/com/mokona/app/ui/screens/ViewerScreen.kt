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
import coil3.compose.AsyncImage
import com.mokona.app.R

/** Full-screen pages on black: swipe between pages, pinch to zoom, double tap to toggle 2.5x. */
@Composable
fun ViewerScreen(urls: List<String>, startIndex: Int, onClose: () -> Unit) {
	val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) { urls.size }
	Box(Modifier.fillMaxSize().background(Color.Black)) {
		HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { urls[it] }) { page ->
			ZoomableImage(urls[page])
		}
		Box(Modifier.safeDrawingPadding().fillMaxSize()) {
			IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
				Icon(Icons.Default.Close, contentDescription = stringResource(R.string.back), tint = Color.White)
			}
			if (urls.size > 1) {
				Text("${pager.currentPage + 1}/${urls.size}", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
			}
		}
	}
}

@Composable
private fun ZoomableImage(url: String) {
	var scale by remember { mutableFloatStateOf(1f) }
	var offset by remember { mutableStateOf(Offset.Zero) }
	val state = rememberTransformableState { zoom, pan, _ ->
		scale = (scale * zoom).coerceIn(1f, 6f)
		offset = if (scale > 1f) offset + pan else Offset.Zero
	}
	AsyncImage(
		model = url,
		contentDescription = null,
		contentScale = ContentScale.Fit,
		modifier = Modifier
			.fillMaxSize()
			.pointerInput(Unit) {
				detectTapGestures(onDoubleTap = {
					if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
				})
			}
			.transformable(state)
			.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
	)
}
