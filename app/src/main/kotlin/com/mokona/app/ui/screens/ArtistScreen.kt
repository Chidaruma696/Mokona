package com.mokona.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mokona.app.R
import com.mokona.app.data.Illust
import com.mokona.app.data.PixivAuth
import com.mokona.app.data.PixivUser
import com.mokona.app.ui.ArtistViewModel

/** An artist: profile header with the follow button, then their works, manga, bookmarks and who they follow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
	userId: Long,
	onBack: () -> Unit,
	onOpen: (Illust) -> Unit,
	onOpenUser: (PixivUser) -> Unit,
	vm: ArtistViewModel = viewModel(key = "artist-$userId") { ArtistViewModel(userId) },
) {
	LaunchedEffect(userId, vm.section) { vm.load() }
	val context = LocalContext.current
	val detail = vm.detail
	val user = detail?.user

	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(user?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
			navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
			actions = {
				IconButton(onClick = { context.openUrl("https://www.pixiv.net/users/$userId") }) {
					Icon(Icons.Default.Share, contentDescription = stringResource(R.string.open_in_pixiv))
				}
			},
		)
		val header: @Composable () -> Unit = {
			Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
				detail?.profile?.backgroundImageUrl?.let {
					AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(120.dp))
				}
				if (user == null) {
					if (vm.error != null) ErrorBox(vm.error!!) else Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
				} else {
					Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						Avatar(user, 64)
						Column(Modifier.weight(1f)) {
							Text(user.name, style = MaterialTheme.typography.titleLarge)
							Text("@${user.account}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
						if (user.id != PixivAuth.userId) FollowButton(user)
					}
					val p = detail.profile
					Text(
						stringResource(R.string.artist_stats, p.totalIllusts + p.totalManga, p.totalIllustBookmarksPublic, p.totalFollowUsers),
						style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(horizontal = 12.dp),
					)
					user.comment?.takeIf { it.isNotBlank() }?.let {
						Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp), maxLines = 6, overflow = TextOverflow.Ellipsis)
					}
					Row(Modifier.padding(horizontal = 4.dp)) {
						p.webpage?.takeIf { it.isNotBlank() }?.let { TextButton(onClick = { context.openUrl(it) }) { Text(stringResource(R.string.website)) } }
						p.twitterUrl?.takeIf { it.isNotBlank() }?.let { TextButton(onClick = { context.openUrl(it) }) { Text("X / Twitter") } }
					}
				}
				LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
					items(ArtistViewModel.Section.entries) { s ->
						FilterChip(selected = vm.section == s, onClick = { vm.section = s }, label = {
							Text(
								when (s) {
									ArtistViewModel.Section.WORKS -> stringResource(R.string.works)
									ArtistViewModel.Section.MANGA -> stringResource(R.string.manga)
									ArtistViewModel.Section.BOOKMARKS -> stringResource(R.string.bookmarks)
									ArtistViewModel.Section.FOLLOWING -> stringResource(R.string.following)
								},
							)
						})
					}
				}
			}
		}
		Box(Modifier.fillMaxSize().navigationBarsPadding()) {
			when (vm.section) {
				ArtistViewModel.Section.WORKS -> IllustGrid(vm.works, onOpen, { vm.loadMore() }, header = header, onRetry = { vm.reload() })
				ArtistViewModel.Section.MANGA -> IllustGrid(vm.manga, onOpen, { vm.loadMore() }, header = header, onRetry = { vm.reload() })
				ArtistViewModel.Section.BOOKMARKS -> IllustGrid(vm.bookmarks, onOpen, { vm.loadMore() }, header = header, onRetry = { vm.reload() })
				ArtistViewModel.Section.FOLLOWING -> Column {
					header()
					UserList(vm.following, onOpenUser = onOpenUser, onOpenIllust = onOpen, onLoadMore = { vm.loadMore() }, onRefresh = { vm.reload() })
				}
			}
		}
	}
}
