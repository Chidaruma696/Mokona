package com.mokona.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mokona.app.R
import com.mokona.app.data.Illust
import com.mokona.app.data.PixivAuth
import com.mokona.app.data.PixivUser
import com.mokona.app.data.RankingMode
import com.mokona.app.data.SearchDuration
import com.mokona.app.data.SearchSort
import com.mokona.app.data.SearchTarget
import com.mokona.app.data.Tag
import com.mokona.app.ui.AppPrefs
import com.mokona.app.ui.BookmarksViewModel
import com.mokona.app.ui.HomeViewModel
import com.mokona.app.ui.RankingViewModel
import com.mokona.app.ui.SearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Illust) -> Unit, onSearch: () -> Unit, vm: HomeViewModel = viewModel()) {
	LaunchedEffect(vm.section) { vm.load() }
	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(stringResource(R.string.home)) },
			actions = { IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) } },
		)
		Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			FilterChip(selected = vm.section == HomeViewModel.Section.RECOMMENDED, onClick = { vm.section = HomeViewModel.Section.RECOMMENDED }, label = { Text(stringResource(R.string.recommended)) })
			FilterChip(selected = vm.section == HomeViewModel.Section.FOLLOWING, onClick = { vm.section = HomeViewModel.Section.FOLLOWING }, label = { Text(stringResource(R.string.following_feed)) })
			FilterChip(selected = vm.section == HomeViewModel.Section.MANGA, onClick = { vm.section = HomeViewModel.Section.MANGA }, label = { Text(stringResource(R.string.manga)) })
		}
		IllustGrid(
			feed = vm.feed,
			onOpen = onOpen,
			onLoadMore = { vm.loadMore() },
			onRetry = { vm.load(force = true) },
			header = if (AppPrefs.kaoBannerVisible && vm.section == HomeViewModel.Section.RECOMMENDED) ({ KeepAndroidOpenBanner(Modifier.padding(bottom = 4.dp)) }) else null,
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(onOpen: (Illust) -> Unit, onSearch: () -> Unit, vm: RankingViewModel = viewModel()) {
	LaunchedEffect(vm.mode, vm.date) { vm.load() }
	var pickDate by remember { mutableStateOf(false) }
	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(vm.date?.let { "${stringResource(R.string.ranking)} · $it" } ?: stringResource(R.string.ranking)) },
			actions = {
				IconButton(onClick = { pickDate = true }) { Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.pick_date)) }
				IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) }
			},
		)
		LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			items(vm.modes, key = { it.id }) { mode ->
				FilterChip(selected = mode == vm.mode, onClick = { vm.select(mode) }, label = { Text(modeLabel(mode)) })
			}
		}
		IllustGrid(feed = vm.feed, onOpen = onOpen, onLoadMore = { vm.loadMore() }, onRetry = { vm.load(force = true) })
	}
	if (pickDate) {
		// Pixiv publishes a day's ranking the day after, so yesterday is the newest selectable day.
		val yesterday = System.currentTimeMillis() - 24L * 3600 * 1000
		val state = rememberDatePickerState(
			initialSelectedDateMillis = vm.date?.let { runCatching { utcFormat().parse(it)?.time }.getOrNull() } ?: yesterday,
			selectableDates = object : androidx.compose.material3.SelectableDates {
				override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= yesterday
			},
		)
		DatePickerDialog(
			onDismissRequest = { pickDate = false },
			confirmButton = {
				TextButton(onClick = {
					state.selectedDateMillis?.let { vm.selectDate(utcFormat().format(Date(it))) }
					pickDate = false
				}) { Text(stringResource(R.string.ok)) }
			},
			dismissButton = {
				TextButton(onClick = { vm.selectDate(null); pickDate = false }) { Text(stringResource(R.string.latest)) }
			},
		) { DatePicker(state = state) }
	}
}

private fun utcFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

@Composable
private fun modeLabel(mode: RankingMode): String {
	val manga = mode.id.endsWith("_manga")
	val base = when (mode.id.removeSuffix("_manga").removeSuffix("_r18")) {
		"day" -> stringResource(R.string.rank_day)
		"week" -> stringResource(R.string.rank_week)
		"month" -> stringResource(R.string.rank_month)
		"day_male" -> stringResource(R.string.rank_male)
		"day_female" -> stringResource(R.string.rank_female)
		"week_original" -> stringResource(R.string.rank_original)
		"week_rookie" -> stringResource(R.string.rank_rookie)
		else -> mode.id
	}
	val label = if (manga) "$base · ${stringResource(R.string.manga)}" else base
	return if (mode.adult) "$label R-18" else label
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onOpen: (Illust) -> Unit, onOpenUser: (PixivUser) -> Unit, vm: SearchViewModel = viewModel()) {
	LaunchedEffect(Unit) { vm.loadTrending() }
	Column(Modifier.fillMaxSize()) {
		OutlinedTextField(
			value = vm.query,
			onValueChange = { vm.onQueryChange(it) },
			modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
			placeholder = { Text(stringResource(if (vm.searchUsers) R.string.search_users_hint else R.string.search_hint)) },
			singleLine = true,
			leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
			trailingIcon = {
				if (vm.query.isNotEmpty()) IconButton(onClick = { vm.clear() }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear)) }
			},
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
			keyboardActions = KeyboardActions(onSearch = { vm.search() }),
		)
		Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			FilterChip(selected = !vm.searchUsers, onClick = { vm.searchUsers = false; vm.clear() }, label = { Text(stringResource(R.string.works)) })
			FilterChip(selected = vm.searchUsers, onClick = { vm.searchUsers = true; vm.clear() }, label = { Text(stringResource(R.string.artists)) })
		}
		if (vm.suggestions.isNotEmpty()) {
			// While typing, the suggestions take the place of the results, one per row, like Materixiv.
			SuggestionList(vm.suggestions, onPick = { vm.pickSuggestion(it) })
			return@Column
		}
		val feed = vm.feed
		val popular = vm.popularFeed
		val userFeed = vm.userFeed
		// Each section keeps its own scroll position while the user flips between them.
		val newestState = rememberLazyStaggeredGridState()
		val popularState = rememberLazyStaggeredGridState()
		when {
			feed != null && popular != null -> {
				SearchHeader(vm)
				SearchSections(vm)
				if (vm.section == SearchViewModel.Section.NEWEST) {
					SearchFilters(vm, withSort = true)
					IllustGrid(feed = feed, onOpen = onOpen, onLoadMore = { vm.loadMore() }, onRetry = { vm.refilter() }, state = newestState)
				} else {
					SearchFilters(vm, withSort = false)
					IllustGrid(
						feed = popular, onOpen = onOpen, onLoadMore = { vm.loadMore() }, onRetry = { vm.refilter() }, state = popularState,
						header = {
							Text(
								stringResource(R.string.popular_preview_note),
								Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
								style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						},
					)
				}
			}
			userFeed != null -> {
				Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
					Text(vm.submitted, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
					TextButton(onClick = { vm.clear() }) { Text(stringResource(R.string.clear)) }
				}
				UserList(feed = userFeed, onOpenUser = onOpenUser, onOpenIllust = onOpen, onLoadMore = { vm.loadMore() }, onRefresh = { vm.search(vm.submitted) })
			}
			else -> {
				Text(stringResource(R.string.trending_tags), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
				val tags = vm.trending.filter { AppPrefs.showAdult || it.illust?.isAdult != true }
				LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					items(tags, key = { it.tag }) { t ->
						TagChip(t.tag, t.translatedName, onClick = { vm.searchUsers = false; vm.search(t.tag, Tag(t.tag, t.translatedName)) })
					}
				}
			}
		}
	}
}

/** One tag per row: its name in the phone's language first, the original Japanese underneath. */
@Composable
private fun SuggestionList(tags: List<Tag>, onPick: (Tag) -> Unit) {
	LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
		items(tags, key = { it.name }) { t ->
			val translated = t.translatedName?.takeIf { it.isNotBlank() && !it.equals(t.name, ignoreCase = true) }
			Column(Modifier.fillMaxWidth().clickable { onPick(t) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
				Text(translated ?: t.name, style = MaterialTheme.typography.bodyLarge)
				if (translated != null) Text(t.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			HorizontalDivider()
		}
	}
}

/**
 * What was searched, the way Pixiv shows a tag: the name in the phone's language on top and the original
 * Japanese tag underneath. With no known translation only the words as typed appear.
 */
@Composable
private fun SearchHeader(vm: SearchViewModel) {
	val tag = vm.submittedTag
	val translated = tag?.translatedName?.takeIf { it.isNotBlank() && !it.equals(tag.name, ignoreCase = true) }
	Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
		Text(translated ?: tag?.name ?: vm.submitted, style = MaterialTheme.typography.titleMedium)
		if (translated != null) Text(tag.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

/** Newest on the left, popular on the right, like the official app and Materixiv. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSections(vm: SearchViewModel) {
	val index = if (vm.section == SearchViewModel.Section.NEWEST) 0 else 1
	SecondaryTabRow(selectedTabIndex = index) {
		Tab(selected = index == 0, onClick = { vm.section = SearchViewModel.Section.NEWEST }, text = { Text(stringResource(R.string.section_newest)) })
		Tab(selected = index == 1, onClick = { vm.section = SearchViewModel.Section.POPULAR }, text = { Text(stringResource(R.string.section_popular)) })
	}
}

/**
 * A tag as Pixiv draws it: the translated name (in the phone's language) on top, the original Japanese tag in
 * small type underneath. A tag with no translation, or whose translation is the same text, is a single line.
 */
@Composable
fun TagChip(name: String, translated: String?, onClick: () -> Unit) {
	val second = translated?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
	AssistChip(
		onClick = onClick,
		label = {
			if (second == null) {
				Text(name)
			} else {
				Column(Modifier.padding(vertical = 4.dp)) {
					Text(second, style = MaterialTheme.typography.labelLarge)
					Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
		},
	)
}

/** Match and time filters as small dropdowns above the results; the newest section also chooses its order. */
@Composable
private fun SearchFilters(vm: SearchViewModel, withSort: Boolean) {
	Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
		if (withSort) Dropdown(
			label = when (vm.sort) {
				SearchSort.DATE_DESC -> stringResource(R.string.sort_newest)
				SearchSort.DATE_ASC -> stringResource(R.string.sort_oldest)
			},
			options = listOf(
				stringResource(R.string.sort_newest) to { vm.sort = SearchSort.DATE_DESC; vm.refilter() },
				stringResource(R.string.sort_oldest) to { vm.sort = SearchSort.DATE_ASC; vm.refilter() },
			),
		)
		Dropdown(
			label = when (vm.target) {
				SearchTarget.PARTIAL_TAGS -> stringResource(R.string.target_partial)
				SearchTarget.EXACT_TAGS -> stringResource(R.string.target_exact)
				SearchTarget.TITLE_CAPTION -> stringResource(R.string.target_title)
			},
			options = listOf(
				stringResource(R.string.target_partial) to { vm.target = SearchTarget.PARTIAL_TAGS; vm.refilter() },
				stringResource(R.string.target_exact) to { vm.target = SearchTarget.EXACT_TAGS; vm.refilter() },
				stringResource(R.string.target_title) to { vm.target = SearchTarget.TITLE_CAPTION; vm.refilter() },
			),
		)
		Dropdown(
			label = when (vm.duration) {
				SearchDuration.ALL -> stringResource(R.string.duration_all)
				SearchDuration.LAST_DAY -> stringResource(R.string.duration_day)
				SearchDuration.LAST_WEEK -> stringResource(R.string.duration_week)
				SearchDuration.LAST_MONTH -> stringResource(R.string.duration_month)
			},
			options = listOf(
				stringResource(R.string.duration_all) to { vm.duration = SearchDuration.ALL; vm.refilter() },
				stringResource(R.string.duration_day) to { vm.duration = SearchDuration.LAST_DAY; vm.refilter() },
				stringResource(R.string.duration_week) to { vm.duration = SearchDuration.LAST_WEEK; vm.refilter() },
				stringResource(R.string.duration_month) to { vm.duration = SearchDuration.LAST_MONTH; vm.refilter() },
			),
		)
	}
}

@Composable
fun Dropdown(label: String, options: List<Pair<String, () -> Unit>>) {
	var open by remember { mutableStateOf(false) }
	Box {
		AssistChip(onClick = { open = true }, label = { Text(label) })
		DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
			for ((text, action) in options) DropdownMenuItem(text = { Text(text) }, onClick = { open = false; action() })
		}
	}
}

/** The user's own bookmarks (public, private, filtered by bookmark tag) and their browsing history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(onOpen: (Illust) -> Unit, onOpenUser: (Long) -> Unit, onSearch: () -> Unit, vm: BookmarksViewModel = viewModel()) {
	LaunchedEffect(Unit) { vm.load(); vm.loadTags() }
	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(stringResource(R.string.bookmarks)) },
			actions = {
				TextButton(onClick = { onOpenUser(PixivAuth.userId) }) { Text(PixivAuth.userName ?: stringResource(R.string.my_profile)) }
				IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) }
			},
		)
		Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			FilterChip(selected = vm.section == BookmarksViewModel.Section.PUBLIC, onClick = { vm.select(BookmarksViewModel.Section.PUBLIC) }, label = { Text(stringResource(R.string.bookmarks_public)) })
			FilterChip(selected = vm.section == BookmarksViewModel.Section.PRIVATE, onClick = { vm.select(BookmarksViewModel.Section.PRIVATE) }, label = { Text(stringResource(R.string.bookmarks_private)) })
			FilterChip(selected = vm.section == BookmarksViewModel.Section.HISTORY, onClick = { vm.select(BookmarksViewModel.Section.HISTORY) }, label = { Text(stringResource(R.string.history)) })
		}
		if (vm.tags.isNotEmpty()) {
			LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				item { FilterChip(selected = vm.tag == null, onClick = { vm.selectTag(null) }, label = { Text(stringResource(R.string.all_tags)) }) }
				items(vm.tags, key = { it.name }) { t ->
					FilterChip(selected = vm.tag == t.name, onClick = { vm.selectTag(t.name) }, label = { Text("${t.name} · ${t.count}") })
				}
			}
		}
		IllustGrid(feed = vm.feed, onOpen = onOpen, onLoadMore = { vm.loadMore() }, onRetry = { vm.load(force = true) })
	}
}

/** Shown on every tab while there is no session. */
@Composable
fun LoginNeeded(onLogin: () -> Unit) {
	Column(Modifier.fillMaxSize().statusBarsPadding().padding(32.dp), verticalArrangement = Arrangement.Center) {
		Card {
			Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(stringResource(R.string.login_needed_title), style = MaterialTheme.typography.titleLarge)
				Text(stringResource(R.string.login_needed_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Button(onClick = onLogin) { Text(stringResource(R.string.login)) }
			}
		}
	}
}
