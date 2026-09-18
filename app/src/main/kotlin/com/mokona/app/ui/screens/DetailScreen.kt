package com.mokona.app.ui.screens

import android.app.WallpaperManager
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mokona.app.R
import com.mokona.app.data.PageTranslator
import androidx.compose.runtime.mutableStateMapOf
import com.mokona.app.data.Illust
import com.mokona.app.data.PixivUser
import com.mokona.app.data.Tag
import com.mokona.app.data.Restrict
import com.mokona.app.ui.AppPrefs
import com.mokona.app.ui.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
	id: Long,
	base: Illust?,
	onBack: () -> Unit,
	onOpen: (Illust) -> Unit,
	onOpenUser: (PixivUser) -> Unit,
	onSearchTag: (Tag) -> Unit,
	onView: (List<String>, Int) -> Unit,
	onRead: (Illust) -> Unit,
	onSeries: (Long) -> Unit,
	onChanged: (Illust) -> Unit,
	vm: DetailViewModel = viewModel(key = "detail-$id"),
) {
	LaunchedEffect(id) { vm.open(base, id) }
	val illust = vm.illust ?: base
	val context = LocalContext.current
	var snack by remember { mutableStateOf<String?>(null) }
	var menu by remember { mutableStateOf(false) }
	var listDialog by remember { mutableStateOf(false) }
	val savedText = stringResource(R.string.saved_pages)
	val savedVideoText = stringResource(R.string.saved_video)
	val privateText = stringResource(R.string.bookmarked_private)

	if (illust == null) {
		Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			if (vm.message != null) ErrorBox(vm.message!!, onRetry = onBack) else CircularProgressIndicator()
		}
		return
	}

	if (listDialog) AddToWallpaperListDialog(illust, onDismiss = { listDialog = false }, onResult = { snack = it })
	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(illust.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
			navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
			actions = {
				// Tap: public bookmark. Long press: private bookmark.
				Box(
					Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).combinedClickable(
						onClick = { vm.toggleBookmark(onChanged) },
						onLongClick = { vm.toggleBookmark(onChanged, Restrict.PRIVATE); snack = privateText },
					),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						if (illust.isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
						contentDescription = stringResource(R.string.bookmark),
						tint = if (illust.isBookmarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
					)
				}
				IconButton(onClick = { share(context, illust) }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share)) }
				if (vm.downloading || vm.encoding) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
				Box {
					IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
					DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
						DropdownMenuItem(
							text = { Text(if (illust.pageCount > 1) stringResource(R.string.download_all, illust.pageCount) else stringResource(R.string.download)) },
							enabled = !vm.downloading,
							onClick = { menu = false; vm.download { n -> snack = savedText.format(n.size) } },
						)
						if (illust.isAnimated) DropdownMenuItem(
							text = { Text(stringResource(R.string.download_video)) },
							enabled = !vm.encoding,
							onClick = { menu = false; vm.downloadVideo { snack = savedVideoText } },
						)
						DropdownMenuItem(text = { Text(stringResource(R.string.open_in_pixiv)) }, onClick = { menu = false; context.openUrl(illust.webUrl) })
						if (!illust.isAdult && !illust.isManga) DropdownMenuItem(text = { Text(stringResource(R.string.add_to_wallpaper_list)) }, onClick = { menu = false; listDialog = true })
						DropdownMenuItem(
							text = { Text(stringResource(R.string.set_wallpaper)) },
							onClick = {
								menu = false
								vm.download { uris ->
									uris.firstOrNull()?.let { uri ->
										runCatching { context.startActivity(WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri)) }
											.onFailure { snack = it.message }
									}
								}
							},
						)
					}
				}
			},
		)
		Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
			val pages = illust.largeUrls
			val pager = rememberPagerState { pages.size }
			val player = vm.ugoira
			// Any picture, one button: 訳 reads and translates the page on screen right here.
			var translating by remember { mutableStateOf(false) }
			var source by remember { mutableStateOf(AppPrefs.ocrSource) }
			var status by remember { mutableStateOf<PageTranslator.Status>(PageTranslator.Status.Idle) }
			val translations = remember { mutableStateMapOf<String, PageTranslator.Result>() }
			LaunchedEffect(translating, pager.currentPage, source) {
				if (!translating) return@LaunchedEffect
				val url = pages.getOrNull(pager.currentPage) ?: return@LaunchedEffect
				if (translations.containsKey("$source:$url")) return@LaunchedEffect
				runCatching { translations["$source:$url"] = PageTranslator.translate(context, url, source) { status = it } }
					.onFailure { status = PageTranslator.Status.Failed(it.message ?: it.javaClass.simpleName) }
			}
			Box(Modifier.fillMaxWidth().background(Color.Black)) {
				if (illust.isAnimated && player != null) {
					// A ugoira plays its frames in place; tap pauses and resumes.
					val bmp = player.frame
					Box(
						Modifier.fillMaxWidth().aspectRatio(illust.aspectRatio.coerceIn(0.6f, 1.8f)).combinedClickable(onClick = { player.playing = !player.playing }),
						contentAlignment = Alignment.Center,
					) {
						if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = illust.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
						else AsyncImage(model = pages.first(), contentDescription = illust.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
						if (bmp == null && player.error == null) CircularProgressIndicator()
						if (!player.playing) Badge("⏸")
					}
					if (bmp != null) LinearProgressIndicator(progress = { player.progress }, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
				} else {
					HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
						val t = if (translating) translations["$source:${pages[page]}"] else null
						if (t != null) {
							TranslatablePage(pages[page], t, ContentScale.Fit, Modifier.fillMaxWidth().aspectRatio(illust.aspectRatio.coerceIn(0.6f, 1.8f)))
						} else {
							AsyncImage(
								model = pages[page],
								contentDescription = illust.title,
								contentScale = ContentScale.Fit,
								modifier = Modifier.fillMaxWidth().aspectRatio(illust.aspectRatio.coerceIn(0.6f, 1.8f)).combinedClickable(onClick = { onView(pages, page) }),
							)
						}
					}
					if (pages.size > 1) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) { Badge("${pager.currentPage + 1}/${pages.size}") }
					IconButton(onClick = { translating = !translating; if (!translating) status = PageTranslator.Status.Idle }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
						Text("訳", color = if (translating) Color(0xFFFFD54F) else Color.White, style = MaterialTheme.typography.titleMedium)
					}
					if (translating) {
						TranslationBar(source, onSource = { source = it; AppPrefs.updateOcrSource(it) }, status = status, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp))
					}
				}
			}
			Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				UserRow(illust.user, onClick = { onOpenUser(illust.user) }, trailing = { FollowButton(illust.user) })
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
						TagChip(tag.name, tag.translatedName, onClick = { onSearchTag(tag) })
					}
				}
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
					if (illust.pageCount > 1) {
						Button(onClick = { onRead(illust) }) { Text(stringResource(R.string.read_pages, illust.pageCount)) }
					}
					illust.series?.let { s -> TextButton(onClick = { onSeries(s.id) }) { Text(stringResource(R.string.view_series)) } }
				}
				illust.series?.let { s ->
					Text(stringResource(R.string.series_of, s.title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onSeries(s.id) })
				}
				if (vm.related.isNotEmpty()) {
					Text(stringResource(R.string.related), style = MaterialTheme.typography.titleMedium)
					LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						items(vm.related.filter { AppPrefs.showAdult || !it.isAdult }, key = { it.id }) { r ->
							Box(Modifier.fillMaxWidth(0.4f)) { IllustCard(r, onClick = { onOpen(r) }) }
						}
					}
				}
				CommentsSection(vm, onOpenUser)
			}
		}
	}
	val message = vm.message ?: snack
	if (message != null) {
		LaunchedEffect(message) { kotlinx.coroutines.delay(3000); vm.message = null; snack = null }
		Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) { Snackbar { Text(message) } }
	}
}

@Composable
private fun CommentsSection(vm: DetailViewModel, onOpenUser: (PixivUser) -> Unit) {
	var text by remember { mutableStateOf("") }
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Text(
			if (vm.totalComments > 0) stringResource(R.string.comments_count, vm.totalComments) else stringResource(R.string.comments),
			style = MaterialTheme.typography.titleMedium,
		)
		for (c in vm.comments) {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				UserRow(c.user, onClick = { onOpenUser(c.user) }, trailing = {
					Text(c.date.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
				})
				c.parentComment?.user?.let {
					Text("↳ ${it.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 46.dp))
				}
				Text(c.comment, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 46.dp))
			}
		}
		if (vm.commentsClosed) {
			Text(stringResource(R.string.comments_closed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			return@Column
		}
		vm.commentsError?.let { e ->
			Text(e, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
			TextButton(onClick = { vm.loadComments() }) { Text(stringResource(R.string.retry)) }
		}
		if (vm.commentsLoading) CircularProgressIndicator(Modifier.size(20.dp))
		else if (vm.hasMoreComments) TextButton(onClick = { vm.loadComments() }) { Text(stringResource(R.string.more_comments)) }
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			OutlinedTextField(
				value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
				placeholder = { Text(stringResource(R.string.write_comment)) }, maxLines = 3,
			)
			TextButton(onClick = { vm.addComment(text) { text = "" } }, enabled = text.isNotBlank()) { Text(stringResource(R.string.send)) }
		}
	}
}

private fun share(context: android.content.Context, illust: Illust) {
	val intent = Intent(Intent.ACTION_SEND).apply {
		type = "text/plain"
		putExtra(Intent.EXTRA_SUBJECT, illust.title)
		putExtra(Intent.EXTRA_TEXT, "${illust.title} · ${illust.user.name}\n${illust.webUrl}")
	}
	runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
