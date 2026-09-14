package com.mokona.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.mokona.app.R
import com.mokona.app.data.Illust
import com.mokona.app.ui.AppPrefs
import com.mokona.app.ui.IllustFeed

fun Context.openUrl(url: String) {
	runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * The grid Mokona is about: every work keeps its real proportion, so a tall
 * illustration is tall and a wide one is wide, nothing is cropped to a square.
 */
@Composable
fun IllustGrid(
	feed: IllustFeed,
	onOpen: (Illust) -> Unit,
	onLoadMore: () -> Unit,
	modifier: Modifier = Modifier,
	contentPadding: PaddingValues = PaddingValues(8.dp),
	header: (@Composable () -> Unit)? = null,
	state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
	val items = feed.visible
	val nearEnd by remember(items.size) {
		derivedStateOf {
			val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
			last >= items.size - 6
		}
	}
	LaunchedEffect(nearEnd, items.size) {
		if (nearEnd && items.isNotEmpty() && feed.hasMore && !feed.loading) onLoadMore()
	}
	Box(modifier.fillMaxSize()) {
		if (items.isEmpty() && feed.loading) {
			CircularProgressIndicator(Modifier.align(Alignment.Center))
		} else if (items.isEmpty() && feed.error != null) {
			ErrorBox(feed.error!!, Modifier.align(Alignment.Center))
		} else {
			LazyVerticalStaggeredGrid(
				columns = StaggeredGridCells.Fixed(AppPrefs.gridColumns),
				state = state,
				contentPadding = contentPadding,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalItemSpacing = 8.dp,
				modifier = Modifier.fillMaxSize(),
			) {
				if (header != null) {
					item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) { header() }
				}
				items(items, key = { it.id }) { illust -> IllustCard(illust, onClick = { onOpen(illust) }) }
				if (feed.loading) {
					item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
						Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
					}
				}
			}
		}
	}
}

@Composable
fun IllustCard(illust: Illust, onClick: () -> Unit) {
	Card(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
		Box {
			AsyncImage(
				model = illust.imageUrls.medium,
				contentDescription = illust.title,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxWidth().aspectRatio(illust.aspectRatio).clip(RoundedCornerShape(12.dp)),
			)
			Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
				if (illust.pageCount > 1) Badge("${illust.pageCount}")
				if (illust.isAnimated) Badge("GIF")
				if (illust.isAdult) Badge("R-18", strong = true)
			}
		}
		Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
			Text(illust.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(illust.user.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
		}
	}
}

@Composable
fun Badge(text: String, strong: Boolean = false) {
	Surface(
		color = if (strong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
		contentColor = if (strong) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
		shape = RoundedCornerShape(6.dp),
	) {
		Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
	}
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
	Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text(stringResource(R.string.something_failed), style = MaterialTheme.typography.titleMedium)
		Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
	}
}

@Composable
fun UserRow(illust: Illust, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
	Row(
		modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		AsyncImage(
			model = illust.user.profileImageUrls.medium,
			contentDescription = null,
			modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)),
			contentScale = ContentScale.Crop,
		)
		Column {
			Text(illust.user.name, style = MaterialTheme.typography.bodyMedium)
			Text("@${illust.user.account}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
}
