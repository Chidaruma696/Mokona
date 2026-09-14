package com.mokona.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mokona.app.R
import com.mokona.app.data.Illust
import com.mokona.app.data.RankingMode
import com.mokona.app.ui.AppPrefs
import com.mokona.app.ui.HomeViewModel
import com.mokona.app.ui.RankingViewModel
import com.mokona.app.ui.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Illust) -> Unit, vm: HomeViewModel = viewModel()) {
	LaunchedEffect(Unit) { vm.load() }
	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(stringResource(R.string.home)) },
			actions = { IconButton(onClick = { vm.load(force = true) }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) } },
		)
		IllustGrid(
			feed = vm.feed,
			onOpen = onOpen,
			onLoadMore = { vm.loadMore() },
			header = if (AppPrefs.kaoBannerVisible) ({ KeepAndroidOpenBanner(Modifier.padding(bottom = 4.dp)) }) else null,
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(onOpen: (Illust) -> Unit, vm: RankingViewModel = viewModel()) {
	LaunchedEffect(vm.mode) { vm.load() }
	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(stringResource(R.string.ranking)) },
			actions = { IconButton(onClick = { vm.load(force = true) }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) } },
		)
		LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			items(vm.modes, key = { it.id }) { mode ->
				FilterChip(selected = mode == vm.mode, onClick = { vm.select(mode) }, label = { Text(modeLabel(mode)) })
			}
		}
		IllustGrid(feed = vm.feed, onOpen = onOpen, onLoadMore = { vm.loadMore() })
	}
}

@Composable
private fun modeLabel(mode: RankingMode): String {
	val base = when (mode.id.removeSuffix("_r18")) {
		"day" -> stringResource(R.string.rank_day)
		"week" -> stringResource(R.string.rank_week)
		"month" -> stringResource(R.string.rank_month)
		"day_male" -> stringResource(R.string.rank_male)
		"day_female" -> stringResource(R.string.rank_female)
		"week_original" -> stringResource(R.string.rank_original)
		"week_rookie" -> stringResource(R.string.rank_rookie)
		else -> mode.id
	}
	return if (mode.adult) "$base R-18" else base
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onOpen: (Illust) -> Unit, vm: SearchViewModel = viewModel()) {
	LaunchedEffect(Unit) { vm.loadTrending() }
	Column(Modifier.fillMaxSize()) {
		OutlinedTextField(
			value = vm.query,
			onValueChange = { vm.query = it },
			modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
			placeholder = { Text(stringResource(R.string.search_hint)) },
			singleLine = true,
			leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
			trailingIcon = {
				if (vm.query.isNotEmpty()) IconButton(onClick = { vm.clear() }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear)) }
			},
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
			keyboardActions = KeyboardActions(onSearch = { vm.search() }),
		)
		val feed = vm.feed
		if (feed == null) {
			Text(stringResource(R.string.trending_tags), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
			val tags = vm.trending.filter { AppPrefs.showAdult || it.illust?.isAdult != true }
			LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				items(tags, key = { it.tag }) { t ->
					AssistChip(onClick = { vm.search(t.tag) }, label = { Text(t.translatedName?.takeIf { it.isNotBlank() }?.let { "${t.tag} · $it" } ?: t.tag) })
				}
			}
		} else {
			Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
				Text("#${vm.submitted}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
				TextButton(onClick = { vm.clear() }) { Text(stringResource(R.string.clear)) }
			}
			IllustGrid(feed = feed, onOpen = onOpen, onLoadMore = { vm.loadMore() })
		}
	}
}

/** Shown on every tab while there is no session. */
@Composable
fun LoginNeeded(onLogin: () -> Unit) {
	Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
		Card {
			Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(stringResource(R.string.login_needed_title), style = MaterialTheme.typography.titleLarge)
				Text(stringResource(R.string.login_needed_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Button(onClick = onLogin) { Text(stringResource(R.string.login)) }
			}
		}
	}
}
