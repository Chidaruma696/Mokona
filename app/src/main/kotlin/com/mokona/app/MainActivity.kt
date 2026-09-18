package com.mokona.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.mokona.app.ui.screens.DownloadBar
import com.mokona.app.data.TranslationModels
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mokona.app.data.Illust
import com.mokona.app.data.PixivAuth
import com.mokona.app.data.Updates
import com.mokona.app.ui.AppPrefs
import com.mokona.app.ui.BookmarksViewModel
import com.mokona.app.ui.HomeViewModel
import com.mokona.app.ui.LinkBridge
import com.mokona.app.ui.LoginBridge
import com.mokona.app.ui.MokonaTheme
import com.mokona.app.ui.RankingViewModel
import com.mokona.app.ui.SearchViewModel
import com.mokona.app.ui.screens.ArtistScreen
import com.mokona.app.ui.screens.BookmarksScreen
import com.mokona.app.ui.screens.DetailScreen
import com.mokona.app.ui.screens.HomeScreen
import com.mokona.app.ui.screens.LoginNeeded
import com.mokona.app.ui.screens.LoginScreen
import com.mokona.app.ui.screens.OnboardingScreen
import com.mokona.app.ui.screens.RankingScreen
import com.mokona.app.ui.screens.ReaderScreen
import com.mokona.app.ui.screens.SeriesScreen
import com.mokona.app.ui.screens.SearchScreen
import com.mokona.app.ui.screens.SettingsScreen
import com.mokona.app.ui.screens.ViewerScreen
import com.mokona.app.ui.screens.WallpaperListsScreen

private enum class Tab(val labelRes: Int) {
	HOME(R.string.home), RANKING(R.string.ranking), SEARCH(R.string.search), BOOKMARKS(R.string.bookmarks), SETTINGS(R.string.settings);
}

private sealed interface Screen {
	/** A work; `illust` is what the list already knew, or null when opened from a link. */
	data class Detail(val id: Long, val illust: Illust?) : Screen
	data class Artist(val userId: Long) : Screen
	data class Viewer(val urls: List<String>, val index: Int) : Screen
	data class Reader(val illust: Illust) : Screen
	data class Series(val seriesId: Long) : Screen
	data object Login : Screen
	data object WallpaperLists : Screen
}

class MainActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		AppPrefs.applyLanguage()
		handleIntent(intent)
		setContent {
			MokonaTheme {
				Surface(Modifier.fillMaxSize()) {
					if (!AppPrefs.onboardingDone) OnboardingScreen(onStart = {}) else MokonaNav()
				}
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleIntent(intent)
	}

	/** pixiv://account/login?code=… from the browser after signing in, or a pixiv.net link to a work or an artist. */
	private fun handleIntent(intent: Intent?) {
		val uri = intent?.data ?: return
		when {
			uri.scheme == "pixiv" -> uri.getQueryParameter("code")?.let { LoginBridge.pendingCode = it }
			uri.host?.endsWith("pixiv.net") == true -> {
				val segments = uri.pathSegments
				val i = segments.indexOfFirst { it == "artworks" || it == "users" }
				val id = segments.getOrNull(i + 1)?.toLongOrNull()
				if (i >= 0 && id != null) {
					if (segments[i] == "artworks") LinkBridge.pendingIllust = id else LinkBridge.pendingUser = id
				}
				uri.getQueryParameter("illust_id")?.toLongOrNull()?.let { LinkBridge.pendingIllust = it }
			}
		}
		intent.data = null
	}
}

@Composable
private fun MokonaNav() {
	var tab by remember { mutableStateOf(Tab.HOME) }
	val stack = remember { mutableStateListOf<Screen>() }
	val homeVm: HomeViewModel = viewModel()
	val rankingVm: RankingViewModel = viewModel()
	val searchVm: SearchViewModel = viewModel()
	val bookmarksVm: BookmarksViewModel = viewModel()

	val top = stack.lastOrNull()
	// Each screen keeps its rememberSaveable state (scroll positions above all) while another one
	// covers it: coming back from a work lands where you left the grid, not at the top.
	val holder = rememberSaveableStateHolder()
	fun keyOf(index: Int) = "$index:${stack[index]}"
	val pop = { holder.removeState(keyOf(stack.lastIndex)); stack.removeAt(stack.lastIndex); Unit }
	val popAll = { stack.indices.forEach { holder.removeState(keyOf(it)) }; stack.clear() }

	val open: (Illust) -> Unit = { stack.add(Screen.Detail(it.id, it)) }
	val openUser: (Long) -> Unit = { id -> if (stack.lastOrNull() != Screen.Artist(id)) stack.add(Screen.Artist(id)) }
	val login: () -> Unit = { if (stack.lastOrNull() != Screen.Login) stack.add(Screen.Login) }
	val changed: (Illust) -> Unit = { i ->
		homeVm.update(i); rankingVm.update(i); searchVm.update(i); bookmarksVm.update(i); bookmarksVm.invalidate()
	}

	// Once a day: is there a newer release on GitHub?
	LaunchedEffect(Unit) { Updates.check() }
	// A code arriving while the login screen is not open (the browser brought us back) opens it.
	val pendingCode = LoginBridge.pendingCode
	LaunchedEffect(pendingCode) { if (pendingCode != null) login() }
	// A pixiv.net link opened with Mokona.
	val pendingIllust = LinkBridge.pendingIllust
	LaunchedEffect(pendingIllust) { if (pendingIllust != null) { LinkBridge.pendingIllust = null; stack.add(Screen.Detail(pendingIllust, null)) } }
	val pendingUser = LinkBridge.pendingUser
	LaunchedEffect(pendingUser) { if (pendingUser != null) { LinkBridge.pendingUser = null; openUser(pendingUser) } }

	BackHandler(enabled = top != null) { pop() }
	val screenKey = if (top == null) "tabs" else keyOf(stack.lastIndex)
	// Android 13+: a download wants to show its notification; ask once, when it happens.
	val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { TranslationModels.wantsPermission = false }
	LaunchedEffect(TranslationModels.wantsPermission) {
		if (TranslationModels.wantsPermission && android.os.Build.VERSION.SDK_INT >= 33) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
	}
	Box(Modifier.fillMaxSize()) {
	holder.SaveableStateProvider(screenKey) { when (top) {
		is Screen.Detail -> DetailScreen(
			id = top.id,
			base = top.illust,
			onBack = { pop() },
			onOpen = open,
			onOpenUser = { openUser(it.id) },
			onSearchTag = { tag -> popAll(); tab = Tab.SEARCH; searchVm.searchUsers = false; searchVm.search(tag.name, tag) },
			onView = { urls, index -> stack.add(Screen.Viewer(urls, index)) },
			onRead = { stack.add(Screen.Reader(it)) },
			onSeries = { stack.add(Screen.Series(it)) },
			onChanged = changed,
		)
		is Screen.Reader -> ReaderScreen(top.illust, onClose = { pop() }, onView = { urls, index -> stack.add(Screen.Viewer(urls, index)) })
		is Screen.Series -> SeriesScreen(top.seriesId, onBack = { pop() }, onOpen = open)
		is Screen.Artist -> ArtistScreen(
			userId = top.userId,
			onBack = { pop() },
			onOpen = open,
			onOpenUser = { openUser(it.id) },
		)
		is Screen.Viewer -> ViewerScreen(top.urls, top.index, onClose = { pop() })
		Screen.Login -> LoginScreen(onBack = { pop() }, onDone = { popAll() })
		Screen.WallpaperLists -> WallpaperListsScreen(onBack = { pop() })
		null -> Scaffold(
			// The top inset belongs to each tab's own TopAppBar; the bottom one to the navigation bar.
			contentWindowInsets = WindowInsets(0, 0, 0, 0),
			bottomBar = {
				Column {
				DownloadBar()
				NavigationBar {
					Tab.entries.forEach { t ->
						NavigationBarItem(
							selected = tab == t,
							onClick = { tab = t },
							icon = {
								Icon(
									when (t) {
										Tab.HOME -> Icons.Default.Home
										Tab.RANKING -> Icons.Default.Star
										Tab.SEARCH -> Icons.Default.Search
										Tab.BOOKMARKS -> Icons.Default.Favorite
										Tab.SETTINGS -> Icons.Default.Settings
									},
									contentDescription = null,
								)
							},
							label = { Text(stringResource(t.labelRes)) },
						)
					}
				}
				}
			},
		) { padding ->
			Box(Modifier.fillMaxSize().padding(padding)) {
				val loggedIn = PixivAuth.isLoggedIn
				// Same idea per tab: flipping between tabs does not reset their scroll either.
				holder.SaveableStateProvider("tab:$tab") {
					when (tab) {
						Tab.HOME -> if (loggedIn) HomeScreen(onOpen = open, onSearch = { tab = Tab.SEARCH }, vm = homeVm) else LoginNeeded(login)
						Tab.RANKING -> if (loggedIn) RankingScreen(onOpen = open, onSearch = { tab = Tab.SEARCH }, vm = rankingVm) else LoginNeeded(login)
						Tab.SEARCH -> if (loggedIn) SearchScreen(onOpen = open, onOpenUser = { openUser(it.id) }, vm = searchVm) else LoginNeeded(login)
						Tab.BOOKMARKS -> if (loggedIn) BookmarksScreen(onOpen = open, onOpenUser = openUser, onSearch = { tab = Tab.SEARCH }, vm = bookmarksVm) else LoginNeeded(login)
						Tab.SETTINGS -> SettingsScreen(onLogin = login, onWallpaperLists = { stack.add(Screen.WallpaperLists) })
					}
				}
			}
		}
	} }
	if (top != null) DownloadBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
	}
}
