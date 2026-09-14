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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.mokona.app.data.PixivUser
import com.mokona.app.data.UserPreview
import com.mokona.app.ui.AppPrefs
import com.mokona.app.ui.FollowState
import com.mokona.app.ui.IllustFeed
import com.mokona.app.ui.UserFeed

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
	onRetry: (() -> Unit)? = null,
	onRefresh: (() -> Unit)? = onRetry,
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
	Refreshable(feed.refreshing, onRefresh, modifier.fillMaxSize()) {
		if (items.isEmpty() && feed.loading) {
			CircularProgressIndicator(Modifier.align(Alignment.Center))
		} else if (items.isEmpty() && feed.error != null) {
			ErrorBox(feed.error!!, Modifier.align(Alignment.Center), onRetry)
		} else if (items.isEmpty() && !feed.loading) {
			Column(Modifier.fillMaxSize()) {
				header?.invoke()
				Text(stringResource(R.string.nothing_here), Modifier.fillMaxWidth().padding(32.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		} else {
			LazyVerticalStaggeredGrid(
				columns = StaggeredGridCells.Fixed(AppPrefs.gridColumns),
				state = state,
				contentPadding = contentPadding,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalItemSpacing = 8.dp,
				modifier = Modifier.fillMaxSize(),
			) {
				if (header != null) item(span = StaggeredGridItemSpan.FullLine) { header() }
				items(items, key = { it.id }) { illust -> IllustCard(illust, onClick = { onOpen(illust) }) }
				if (feed.loading) {
					item(span = StaggeredGridItemSpan.FullLine) {
						Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
					}
				}
			}
		}
	}
}

/** Pull down from the top to reload, when the list knows how. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(refreshing: Boolean, onRefresh: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
	if (onRefresh == null) Box(modifier, content = content)
	else PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier, content = content)
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
			if (illust.isBookmarked) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) { Badge("♥", strong = true) }
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
fun Avatar(user: PixivUser, size: Int = 36) {
	AsyncImage(
		model = user.profileImageUrls.medium,
		contentDescription = null,
		modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 2).dp)),
		contentScale = ContentScale.Crop,
	)
}

/** Avatar, name and handle; tapping opens the artist. */
@Composable
fun UserRow(user: PixivUser, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
	Row(
		modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Avatar(user)
		Column(Modifier.weight(1f)) {
			Text(user.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text("@${user.account}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
		}
		trailing?.invoke()
	}
}

/** Follow / following toggle, shared by the artist page and every user list. */
@Composable
fun FollowButton(user: PixivUser, modifier: Modifier = Modifier) {
	val scope = rememberCoroutineScope()
	val followed = FollowState.isFollowed(user)
	if (followed) {
		OutlinedButton(onClick = { FollowState.toggle(scope, user) }, modifier = modifier) { Text(stringResource(R.string.following)) }
	} else {
		FilledTonalButton(onClick = { FollowState.toggle(scope, user) }, modifier = modifier) { Text(stringResource(R.string.follow)) }
	}
}

/** A list of people, each with a follow button and a strip of their latest works. */
@Composable
fun UserList(
	feed: UserFeed,
	onOpenUser: (PixivUser) -> Unit,
	onOpenIllust: (Illust) -> Unit,
	onLoadMore: () -> Unit,
	modifier: Modifier = Modifier,
	onRefresh: (() -> Unit)? = null,
	state: LazyListState = rememberLazyListState(),
) {
	val items = feed.visible
	val nearEnd by remember(items.size) {
		derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - 4 }
	}
	LaunchedEffect(nearEnd, items.size) { if (nearEnd && items.isNotEmpty() && feed.hasMore && !feed.loading) onLoadMore() }
	Refreshable(feed.refreshing, onRefresh, modifier.fillMaxSize()) {
		if (items.isEmpty() && feed.loading) {
			CircularProgressIndicator(Modifier.align(Alignment.Center))
		} else if (items.isEmpty() && feed.error != null) {
			ErrorBox(feed.error!!, Modifier.align(Alignment.Center))
		} else {
			LazyColumn(state = state, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				items(items, key = { it.user.id }) { p -> UserCard(p, onOpenUser = { onOpenUser(p.user) }, onOpenIllust = onOpenIllust) }
				if (feed.loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
			}
		}
	}
}

@Composable
fun UserCard(preview: UserPreview, onOpenUser: () -> Unit, onOpenIllust: (Illust) -> Unit) {
	Card(onClick = onOpenUser, shape = RoundedCornerShape(12.dp)) {
		Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			UserRow(preview.user, trailing = { FollowButton(preview.user) })
			val shown = preview.illusts.filter { AppPrefs.showAdult || !it.isAdult }.take(3)
			if (shown.isNotEmpty()) {
				Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					for (i in shown) {
						AsyncImage(
							model = i.imageUrls.squareMedium,
							contentDescription = i.title,
							contentScale = ContentScale.Crop,
							modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onOpenIllust(i) },
						)
					}
					repeat(3 - shown.size) { Box(Modifier.weight(1f)) }
				}
			}
		}
	}
}
