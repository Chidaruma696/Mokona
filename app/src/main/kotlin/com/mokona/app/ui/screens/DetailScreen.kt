package com.mokona.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mokona.app.R
import com.mokona.app.data.Illust
import com.mokona.app.ui.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
	base: Illust,
	onBack: () -> Unit,
	onOpen: (Illust) -> Unit,
	onSearchTag: (String) -> Unit,
	onChanged: (Illust) -> Unit,
	vm: DetailViewModel = viewModel(key = "detail-${base.id}"),
) {
	LaunchedEffect(base.id) { vm.open(base) }
	val illust = vm.illust ?: base
	val context = LocalContext.current
	var snack by remember { mutableStateOf<String?>(null) }
	val savedText = stringResource(R.string.saved_pages)

	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(illust.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
			navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
			actions = {
				IconButton(onClick = { vm.toggleBookmark(onChanged) }) {
					Icon(
						if (illust.isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
						contentDescription = stringResource(R.string.bookmark),
						tint = if (illust.isBookmarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
					)
				}
				IconButton(onClick = { context.openUrl("https://www.pixiv.net/artworks/${illust.id}") }) {
					Icon(Icons.Default.Share, contentDescription = stringResource(R.string.open_in_pixiv))
				}
			},
		)
		Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
			val pages = illust.largeUrls
			val pager = rememberPagerState { pages.size }
			Box(Modifier.fillMaxWidth().background(Color.Black)) {
				HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
					AsyncImage(
						model = pages[page],
						contentDescription = illust.title,
						contentScale = ContentScale.Fit,
						modifier = Modifier.fillMaxWidth().aspectRatio(illust.aspectRatio.coerceIn(0.6f, 1.8f)),
					)
				}
				if (pages.size > 1) {
					Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) { Badge("${pager.currentPage + 1}/${pages.size}") }
				}
			}
			Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				UserRow(illust)
				Text(illust.title, style = MaterialTheme.typography.titleLarge)
				if (illust.caption.isNotBlank()) {
					Text(
						illust.caption.replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), ""),
						style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				Text(
					stringResource(R.string.stats, illust.totalView, illust.totalBookmarks, illust.createDate.take(10)),
					style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					items(illust.tags, key = { it.name }) { tag ->
						AssistChip(
							onClick = { onSearchTag(tag.name) },
							label = { Text(tag.translatedName?.takeIf { it.isNotBlank() }?.let { "${tag.name} · $it" } ?: tag.name) },
						)
					}
				}
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					FilledTonalButton(onClick = { vm.download { n -> snack = savedText.format(n) } }, enabled = !vm.downloading) {
						if (vm.downloading) CircularProgressIndicator(Modifier.padding(end = 8.dp).aspectRatio(1f).fillMaxWidth(0.1f)) else Unit
						Text(stringResource(R.string.download))
					}
					Button(onClick = { vm.toggleBookmark(onChanged) }) {
						Text(if (illust.isBookmarked) stringResource(R.string.bookmarked) else stringResource(R.string.bookmark))
					}
				}
				if (vm.related.isNotEmpty()) {
					Text(stringResource(R.string.related), style = MaterialTheme.typography.titleMedium)
					LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						items(vm.related.filter { com.mokona.app.ui.AppPrefs.showAdult || !it.isAdult }, key = { it.id }) { r ->
							Box(Modifier.fillMaxWidth(0.4f)) { IllustCard(r, onClick = { onOpen(r) }) }
						}
					}
				}
			}
		}
	}
	val message = vm.message ?: snack
	if (message != null) {
		LaunchedEffect(message) { kotlinx.coroutines.delay(3000); vm.message = null; snack = null }
		Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) { Snackbar { Text(message) } }
	}
}
