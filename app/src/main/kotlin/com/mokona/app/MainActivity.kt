package com.mokona.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mokona.app.data.Illust
import com.mokona.app.data.PixivAuth
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
import com.mokona.app.ui.screens.SearchScreen
import com.mokona.app.ui.screens.SettingsScreen
import com.mokona.app.ui.screens.ViewerScreen

private enum class Tab(val labelRes: Int) {
	HOME(R.string.home), RANKING(R.string.ranking), SEARCH(R.string.search), BOOKMARKS(R.string.bookmarks), SETTINGS(R.string.settings);
}

private sealed interface Screen {
	/** A work; `illust` is what the list already knew, or null when opened from a link. */
	data class Detail(val id: Long, val illust: Illust?) : Screen
	data class Artist(val userId: Long) : Screen
	data class Viewer(val urls: List<String>, val index: Int) : Screen
	data object Login : Screen
}

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
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
	BackHandler(enabled = top != null) { stack.removeAt(stack.lastIndex) }

	val open: (Illust) -> Unit = { stack.add(Screen.Detail(it.id, it)) }
	val openUser: (Long) -> Unit = { id -> if (stack.lastOrNull() != Screen.Artist(id)) stack.add(Screen.Artist(id)) }
	val login: () -> Unit = { if (stack.lastOrNull() != Screen.Login) stack.add(Screen.Login) }
	val changed: (Illust) -> Unit = { i ->
		homeVm.update(i); rankingVm.update(i); searchVm.update(i); bookmarksVm.update(i); bookmarksVm.invalidate()
	}

	// A code arriving while the login screen is not open (the browser brought us back) opens it.
	val pendingCode = LoginBridge.pendingCode
	LaunchedEffect(pendingCode) { if (pendingCode != null) login() }
	// A pixiv.net link opened with Mokona.
	val pendingIllust = LinkBridge.pendingIllust
	LaunchedEffect(pendingIllust) { if (pendingIllust != null) { LinkBridge.pendingIllust = null; stack.add(Screen.Detail(pendingIllust, null)) } }
	val pendingUser = LinkBridge.pendingUser
	LaunchedEffect(pendingUser) { if (pendingUser != null) { LinkBridge.pendingUser = null; openUser(pendingUser) } }

	when (top) {
		is Screen.Detail -> DetailScreen(
			id = top.id,
			base = top.illust,
			onBack = { stack.removeAt(stack.lastIndex) },
			onOpen = open,
			onOpenUser = { openUser(it.id) },
			onSearchTag = { tag -> stack.clear(); tab = Tab.SEARCH; searchVm.searchUsers = false; searchVm.search(tag) },
			onView = { urls, index -> stack.add(Screen.Viewer(urls, index)) },
			onChanged = changed,
		)
		is Screen.Artist -> ArtistScreen(
			userId = top.userId,
			onBack = { stack.removeAt(stack.lastIndex) },
			onOpen = open,
			onOpenUser = { openUser(it.id) },
		)
		is Screen.Viewer -> ViewerScreen(top.urls, top.index, onClose = { stack.removeAt(stack.lastIndex) })
		Screen.Login -> LoginScreen(onBack = { stack.removeAt(stack.lastIndex) }, onDone = { stack.clear() })
		null -> Scaffold(
			// The top inset belongs to each tab's own TopAppBar; the bottom one to the navigation bar.
			contentWindowInsets = WindowInsets(0, 0, 0, 0),
			bottomBar = {
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
			},
		) { padding ->
			Box(Modifier.fillMaxSize().padding(padding)) {
				val loggedIn = PixivAuth.isLoggedIn
				when (tab) {
					Tab.HOME -> if (loggedIn) HomeScreen(onOpen = open, onSearch = { tab = Tab.SEARCH }, vm = homeVm) else LoginNeeded(login)
					Tab.RANKING -> if (loggedIn) RankingScreen(onOpen = open, onSearch = { tab = Tab.SEARCH }, vm = rankingVm) else LoginNeeded(login)
					Tab.SEARCH -> if (loggedIn) SearchScreen(onOpen = open, onOpenUser = { openUser(it.id) }, vm = searchVm) else LoginNeeded(login)
					Tab.BOOKMARKS -> if (loggedIn) BookmarksScreen(onOpen = open, onOpenUser = openUser, onSearch = { tab = Tab.SEARCH }, vm = bookmarksVm) else LoginNeeded(login)
					Tab.SETTINGS -> SettingsScreen(onLogin = login)
				}
			}
		}
	}
}
