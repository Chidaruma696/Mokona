package com.mokona.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.mokona.app.ui.AppPrefs
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mokona.app.R
import com.mokona.app.data.PageTranslator

/**
 * A page with its translated bubbles drawn on top. The overlay follows the picture: it is laid
 * out inside the same box, scaled from bitmap pixels to the drawn size, so zooming the parent
 * moves both together. Tap a bubble to peek at the original text.
 */
@Composable
fun TranslatablePage(url: String, translation: PageTranslator.Result?, contentScale: ContentScale, modifier: Modifier = Modifier) {
	BoxWithConstraints(modifier) {
		AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
		if (translation != null && translation.bubbles.isNotEmpty()) {
			val density = LocalDensity.current
			val cw = with(density) { maxWidth.toPx() }
			val ch = with(density) { maxHeight.toPx() }
			val scale = if (contentScale == ContentScale.Fit) minOf(cw / translation.width, ch / translation.height) else cw / translation.width
			val drawnW = translation.width * scale
			val drawnH = translation.height * scale
			val ox = if (contentScale == ContentScale.Fit) (cw - drawnW) / 2f else 0f
			val oy = if (contentScale == ContentScale.Fit) (ch - drawnH) / 2f else 0f
			val textSize = AppPrefs.translationTextSize.sp
			// A bubble is as wide as the original box, but at least wide enough to read, and as tall as
			// the text needs at the chosen size: readable first, faithful to the box second.
			val minW = (cw * 0.22f).toInt()
			val maxW = (cw * 0.6f).toInt()
			translation.bubbles.forEach { b ->
				var original by remember(b) { mutableStateOf(false) }
				val cx = ox + (b.box.left + b.box.width() / 2f) * scale
				val cy = oy + (b.box.top + b.box.height() / 2f) * scale
				val boxW = (b.box.width() * scale).toInt().coerceIn(minW, maxW)
				Box(
					Modifier.layout { measurable, _ ->
						val p = measurable.measure(Constraints(minWidth = boxW, maxWidth = maxW))
						val x = (cx - p.width / 2f).toInt().coerceIn(0, (cw - p.width).toInt().coerceAtLeast(0))
						val y = (cy - p.height / 2f).toInt().coerceIn(0, (ch - p.height).toInt().coerceAtLeast(0))
						layout(cw.toInt(), ch.toInt()) { p.place(x, y) }
					},
				) {
					Text(
						if (original) b.original else b.translated,
						color = Color.Black,
						fontSize = textSize,
						lineHeight = textSize * 1.15f,
						textAlign = TextAlign.Center,
						modifier = Modifier
							.background(if (original) Color(0xF2FFF7C2) else Color(0xF2FFFFFF), RoundedCornerShape(4.dp))
							.clickable { original = !original }
							.padding(horizontal = 4.dp, vertical = 2.dp),
					)
				}
			}
		}
	}
}

/** Script chips and the state of the translator, shown while the overlay is on. */
@Composable
fun TranslationBar(source: PageTranslator.Source, onSource: (PageTranslator.Source) -> Unit, status: PageTranslator.Status, modifier: Modifier = Modifier) {
	Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
		PageTranslator.Source.entries.forEach { s ->
			FilterChip(selected = source == s, onClick = { onSource(s) }, label = {
				Text(
					when (s) {
						PageTranslator.Source.JAPANESE -> stringResource(R.string.lang_japanese)
						PageTranslator.Source.CHINESE -> stringResource(R.string.lang_chinese)
						PageTranslator.Source.KOREAN -> stringResource(R.string.lang_korean)
						PageTranslator.Source.ENGLISH -> stringResource(R.string.lang_english)
					},
				)
			})
		}
		androidx.compose.material3.IconButton(onClick = { AppPrefs.updateTranslationTextSize(AppPrefs.translationTextSize - 2) }) { Text("A−", color = Color.White, style = MaterialTheme.typography.labelLarge) }
		androidx.compose.material3.IconButton(onClick = { AppPrefs.updateTranslationTextSize(AppPrefs.translationTextSize + 2) }) { Text("A+", color = Color.White, style = MaterialTheme.typography.labelLarge) }
		Text(
			when (status) {
				PageTranslator.Status.Idle -> ""
				PageTranslator.Status.Loading -> stringResource(R.string.translate_loading)
				PageTranslator.Status.DownloadingModel -> stringResource(R.string.translate_downloading)
				PageTranslator.Status.Recognizing -> stringResource(R.string.translate_recognizing)
				PageTranslator.Status.Translating -> stringResource(R.string.translate_translating)
				is PageTranslator.Status.Failed -> status.message
			},
			color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp),
		)
	}
}
