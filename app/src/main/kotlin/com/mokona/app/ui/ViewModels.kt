package com.mokona.app.ui

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mokona.app.MokonaApp
import com.mokona.app.data.Illust
import com.mokona.app.data.IllustsPage
import com.mokona.app.data.PixivApi
import com.mokona.app.data.PixivAuth
import com.mokona.app.data.RankingMode
import com.mokona.app.data.TrendTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A paged list of works with the adult filter applied at display time. */
class IllustFeed(private val fetchFirst: suspend () -> IllustsPage) {
	var items by mutableStateOf<List<Illust>>(emptyList())
		private set
	var loading by mutableStateOf(false)
		private set
	var error by mutableStateOf<String?>(null)
		private set
	private var nextUrl: String? = null
	private var job: Job? = null

	/** Works the current settings allow. Muted works stay hidden too. */
	val visible: List<Illust> get() = items.filter { (AppPrefs.showAdult || !it.isAdult) && !it.isMuted }
	val hasMore: Boolean get() = nextUrl != null

	fun load(scope: kotlinx.coroutines.CoroutineScope, force: Boolean = false) {
		if (loading) return
		if (items.isNotEmpty() && !force) return
		job?.cancel()
		job = scope.launch {
			loading = true; error = null
			runCatching { fetchFirst() }
				.onSuccess { items = it.illusts; nextUrl = it.nextUrl }
				.onFailure { error = it.message ?: it.toString() }
			loading = false
		}
	}

	fun loadMore(scope: kotlinx.coroutines.CoroutineScope) {
		val url = nextUrl ?: return
		if (loading) return
		job = scope.launch {
			loading = true
			runCatching { PixivApi.nextPage(url) }
				.onSuccess { items = items + it.illusts; nextUrl = it.nextUrl }
				.onFailure { error = it.message }
			loading = false
		}
	}

	fun update(illust: Illust) {
		items = items.map { if (it.id == illust.id) illust else it }
	}
}

class HomeViewModel : ViewModel() {
	val feed = IllustFeed { PixivApi.recommended() }
	fun load(force: Boolean = false) = feed.load(viewModelScope, force)
	fun loadMore() = feed.loadMore(viewModelScope)
}

class RankingViewModel : ViewModel() {
	var mode by mutableStateOf(RankingMode.all.first())
		private set
	private val feeds = HashMap<String, IllustFeed>()
	val feed: IllustFeed get() = feeds.getOrPut(mode.id) { IllustFeed { PixivApi.ranking(mode.id) } }
	val modes: List<RankingMode> get() = RankingMode.all.filter { AppPrefs.showAdult || !it.adult }

	fun select(m: RankingMode) { mode = m; load() }
	fun load(force: Boolean = false) = feed.load(viewModelScope, force)
	fun loadMore() = feed.loadMore(viewModelScope)
}

class SearchViewModel : ViewModel() {
	var query by mutableStateOf("")
	var submitted by mutableStateOf("")
		private set
	var trending by mutableStateOf<List<TrendTag>>(emptyList())
		private set
	var feed by mutableStateOf<IllustFeed?>(null)
		private set

	fun loadTrending() {
		if (trending.isNotEmpty()) return
		viewModelScope.launch { runCatching { PixivApi.trendingTags() }.onSuccess { trending = it } }
	}

	fun search(word: String = query) {
		val w = word.trim()
		if (w.isEmpty()) return
		query = w; submitted = w
		feed = IllustFeed { PixivApi.search(w) }.also { it.load(viewModelScope) }
	}

	fun clear() { query = ""; submitted = ""; feed = null }
	fun loadMore() = feed?.loadMore(viewModelScope)
}

class DetailViewModel : ViewModel() {
	var illust by mutableStateOf<Illust?>(null)
		private set
	var related by mutableStateOf<List<Illust>>(emptyList())
		private set
	var message by mutableStateOf<String?>(null)
	var downloading by mutableStateOf(false)
		private set

	fun open(base: Illust) {
		illust = base
		related = emptyList()
		viewModelScope.launch {
			runCatching { PixivApi.detail(base.id) }.onSuccess { illust = it }
			runCatching { PixivApi.related(base.id) }.onSuccess { related = it.illusts }
		}
	}

	fun toggleBookmark(onChanged: (Illust) -> Unit) {
		val i = illust ?: return
		val next = i.copy(isBookmarked = !i.isBookmarked, totalBookmarks = i.totalBookmarks + if (i.isBookmarked) -1 else 1)
		illust = next
		onChanged(next)
		viewModelScope.launch {
			runCatching { PixivApi.bookmark(i.id, add = !i.isBookmarked) }.onFailure {
				illust = i; onChanged(i); message = it.message
			}
		}
	}

	/** Saves every page of the work in Pictures/Mokona through MediaStore (no storage permission needed). */
	fun download(onDone: (Int) -> Unit) {
		val i = illust ?: return
		if (downloading) return
		viewModelScope.launch {
			downloading = true
			var saved = 0
			for ((index, url) in i.originalUrls.withIndex()) {
				runCatching {
					val bytes = PixivApi.download(url)
					withContext(Dispatchers.IO) {
						val ext = url.substringAfterLast('.', "jpg").take(4)
						val resolver = MokonaApp.appContext.contentResolver
						val values = ContentValues().apply {
							put(MediaStore.Images.Media.DISPLAY_NAME, "pixiv_${i.id}_p$index.$ext")
							put(MediaStore.Images.Media.MIME_TYPE, if (ext == "png") "image/png" else "image/jpeg")
							put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mokona")
						}
						val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore")
						resolver.openOutputStream(uri)!!.use { it.write(bytes) }
					}
				}.onSuccess { saved++ }.onFailure { message = it.message }
			}
			downloading = false
			onDone(saved)
		}
	}
}

/** The OAuth code that came back from the browser, waiting for the login screen to use it. */
object LoginBridge {
	var pendingCode by mutableStateOf<String?>(null)
}

class LoginViewModel : ViewModel() {
	var busy by mutableStateOf(false)
		private set
	var error by mutableStateOf<String?>(null)
		private set

	fun startUrl(): String = PixivAuth.beginLogin()

	fun finish(code: String, onDone: () -> Unit) {
		if (busy) return
		viewModelScope.launch {
			busy = true; error = null
			runCatching { PixivAuth.finishLogin(code) }
				.onSuccess { onDone() }
				.onFailure { error = it.message }
			busy = false
		}
	}
}
