package com.mokona.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mokona.app.R
import com.mokona.app.data.PageTranslator
import com.mokona.app.ui.AppPrefs

private val NoteColor = Color(0xFFFFD54F)

/**
 * A page with its translation. Two looks, chosen in Settings:
 *  - notes (default, like Danbooru's): each line gets a thin numbered outline on the picture and
 *    the translations live in a list beside it (see [TranslationNotes]); tap a box or a row to pair them;
 *  - over the picture: the translated text drawn on the bubble, tap to peek at the original.
 * The overlay is laid out inside the same box as the picture, scaled from bitmap pixels to the
 * drawn size, so zooming the parent moves both together.
 */
@Composable
fun TranslatablePage(
	url: String,
	translation: PageTranslator.Result?,
	contentScale: ContentScale,
	modifier: Modifier = Modifier,
	selected: Int? = null,
	onSelect: (Int) -> Unit = {},
) {
	BoxWithConstraints(modifier) {
		AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
		if (translation != null && translation.bubbles.isNotEmpty()) {
			val density = LocalDensity.current
			val cw = with(density) { maxWidth.toPx() }
			val ch = with(density) { maxHeight.toPx() }
			val scale = if (contentScale == ContentScale.Fit) minOf(cw / translation.width, ch / translation.height) else cw / translation.width
			val ox = if (contentScale == ContentScale.Fit) (cw - translation.width * scale) / 2f else 0f
			val oy = if (contentScale == ContentScale.Fit) (ch - translation.height * scale) / 2f else 0f
			if (AppPrefs.translationNotes) {
				translation.bubbles.forEachIndexed { i, b ->
					val x = (ox + b.box.left * scale).toInt()
					val y = (oy + b.box.top * scale).toInt()
					val w = with(density) { (b.box.width() * scale).coerceAtLeast(12f).toDp() }
					val h = with(density) { (b.box.height() * scale).coerceAtLeast(12f).toDp() }
					val on = selected == i
					Box(
						Modifier
							.offset { IntOffset(x, y) }
							.size(w, h)
							.background(if (on) NoteColor.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(3.dp))
							.border(if (on) 2.dp else 1.dp, NoteColor.copy(alpha = if (on) 1f else 0.8f), RoundedCornerShape(3.dp))
							.clickable { onSelect(i) },
					) {
						NoteBadge(i + 1, Modifier.offset((-6).dp, (-6).dp))
					}
				}
			} else {
				val textSize = AppPrefs.translationTextSize.sp
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
							color = Color.Black, fontSize = textSize, lineHeight = textSize * 1.15f, textAlign = TextAlign.Center,
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
}

@Composable
private fun NoteBadge(n: Int, modifier: Modifier = Modifier) {
	Box(modifier.size(18.dp).background(NoteColor, CircleShape), contentAlignment = Alignment.Center) {
		Text("$n", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
	}
}

/**
 * The notes beside the picture: one row per line, numbered like its box, the translation big and
 * readable and the original underneath. `dark` for the black reader and viewer.
 */
@Composable
fun TranslationNotes(translation: PageTranslator.Result, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
	val textSize = AppPrefs.translationTextSize.sp
	val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
	val dim = if (dark) Color(0xFFBDBDBD) else MaterialTheme.colorScheme.onSurfaceVariant
	Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
		if (translation.bubbles.isEmpty()) {
			Text(stringResource(R.string.no_text_found), color = dim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
		}
		translation.bubbles.forEachIndexed { i, b ->
			val on = selected == i
			Row(
				Modifier
					.fillMaxWidth()
					.background(if (on) NoteColor.copy(alpha = if (dark) 0.18f else 0.3f) else Color.Transparent, RoundedCornerShape(8.dp))
					.clickable { onSelect(i) }
					.padding(horizontal = 10.dp, vertical = 6.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				NoteBadge(i + 1, Modifier.padding(top = 3.dp))
				Column {
					Text(b.translated, color = fg, fontSize = textSize, lineHeight = textSize * 1.3f)
					if (b.original != b.translated) Text(b.original, color = dim, fontSize = textSize * 0.8f, lineHeight = textSize * 1.05f, modifier = Modifier.padding(top = 2.dp))
				}
			}
		}
	}
}

/** Notes panel that floats over the bottom of a full-screen page (reader, viewer): scrolls, takes up to 40 % of the height. */
@Composable
fun TranslationNotesSheet(translation: PageTranslator.Result, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
	BoxWithConstraints(modifier.fillMaxWidth()) {
		Column(
			Modifier
				.fillMaxWidth()
				.background(Color(0xE6101010), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
				.padding(top = 4.dp)
				.heightIn(max = maxHeight * 0.4f)
				.verticalScroll(rememberScrollState()),
		) {
			TranslationNotes(translation, selected, onSelect, dark = true)
		}
	}
}

@Composable
fun sourceName(source: PageTranslator.Source): String = when (source) {
	PageTranslator.Source.JAPANESE -> stringResource(R.string.lang_japanese)
	PageTranslator.Source.CHINESE -> stringResource(R.string.lang_chinese)
	PageTranslator.Source.KOREAN -> stringResource(R.string.lang_korean)
	PageTranslator.Source.ENGLISH -> stringResource(R.string.lang_english)
}

/** The language the page turned out to be in, text size and the state of the translator. */
@Composable
fun TranslationBar(detected: PageTranslator.Source?, status: PageTranslator.Status, modifier: Modifier = Modifier) {
	Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
		if (detected != null) {
			Text(stringResource(R.string.detected_language, sourceName(detected)), color = NoteColor, style = MaterialTheme.typography.labelMedium)
		}
		IconButton(onClick = { AppPrefs.updateTranslationTextSize(AppPrefs.translationTextSize - 2) }) { Text("A−", color = Color.White, style = MaterialTheme.typography.labelLarge) }
		IconButton(onClick = { AppPrefs.updateTranslationTextSize(AppPrefs.translationTextSize + 2) }) { Text("A+", color = Color.White, style = MaterialTheme.typography.labelLarge) }
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
