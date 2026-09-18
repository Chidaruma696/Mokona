package com.mokona.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mokona.app.MokonaApp
import com.mokona.app.R
import com.mokona.app.data.PixivException
import com.mokona.app.data.BookmarkTag
import com.mokona.app.data.Comment
import com.mokona.app.data.Illust
import com.mokona.app.data.IllustsPage
import com.mokona.app.data.PixivApi
import com.mokona.app.data.PixivAuth
import com.mokona.app.data.PixivUser
import com.mokona.app.data.RankingMode
import com.mokona.app.data.Restrict
import com.mokona.app.data.SearchDuration
import com.mokona.app.data.SearchSort
import com.mokona.app.data.SearchTarget
import com.mokona.app.data.Tag
import com.mokona.app.data.TrendTag
import com.mokona.app.data.UgoiraMetadata
import com.mokona.app.data.UgoiraVideo
import com.mokona.app.data.UserDetail
import com.mokona.app.data.UserPreview
import com.mokona.app.data.UsersPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** A paged list from Pixiv: first page from one call, the rest through next_url. */
open class Feed<T>(
	private val fetchFirst: suspend () -> Pair<List<T>, String?>,
	private val fetchNext: suspend (String) -> Pair<List<T>, String?>,
) {
	var items by mutableStateOf<List<T>>(emptyList())
		protected set
	var loading by mutableStateOf(false)
		private set
	var error by mutableStateOf<String?>(null)
		private set
	/** True while a pull-to-refresh reload is running (the old items stay on screen meanwhile). */
	var refreshing by mutableStateOf(false)
		private set
	private var nextUrl: String? = null
	private var job: Job? = null

	val hasMore: Boolean get() = nextUrl != null

	fun load(scope: CoroutineScope, force: Boolean = false) {
		if (loading) return
		if (items.isNotEmpty() && !force) return
		job?.cancel()
		job = scope.launch {
			loading = true; error = null
			refreshing = force && items.isNotEmpty()
			runCatching { fetchFirst() }
				.onSuccess { (list, next) -> items = list; nextUrl = next }
				.onFailure { error = it.message ?: it.toString() }
			loading = false; refreshing = false
		}
	}

	fun loadMore(scope: CoroutineScope) {
		val url = nextUrl ?: return
		if (loading) return
		job = scope.launch {
			loading = true
			runCatching { fetchNext(url) }
				.onSuccess { (list, next) -> items = items + list; nextUrl = next }
				.onFailure { error = it.message }
			loading = false
		}
	}

	fun replace(transform: (T) -> T) { items = items.map(transform) }
}

/** Works, with the adult filter applied at display time. */
class IllustFeed(first: suspend () -> IllustsPage) : Feed<Illust>(
	fetchFirst = { first().let { it.illusts to it.nextUrl } },
	fetchNext = { PixivApi.nextPage(it).let { p -> p.illusts to p.nextUrl } },
) {
	/** Works the current settings allow. Muted works stay hidden too. */
	val visible: List<Illust> get() = items.filter { (AppPrefs.showAdult || !it.isAdult) && !it.isMuted }
	fun update(illust: Illust) = replace { if (it.id == illust.id) illust else it }
}

class UserFeed(first: suspend () -> UsersPage) : Feed<UserPreview>(
	fetchFirst = { first().let { it.userPreviews to it.nextUrl } },
	fetchNext = { PixivApi.nextUsers(it).let { p -> p.userPreviews to p.nextUrl } },
) {
	val visible: List<UserPreview> get() = items.filter { !it.isMuted }
}

/** Follow changes made in this session, so every list and screen agrees without refetching. */
object FollowState {
	private val overrides = mutableStateMapOf<Long, Boolean>()
	fun isFollowed(user: PixivUser): Boolean = overrides[user.id] ?: user.isFollowed
	var error by mutableStateOf<String?>(null)

	fun toggle(scope: CoroutineScope, user: PixivUser) {
		val next = !isFollowed(user)
		overrides[user.id] = next
		scope.launch {
			runCatching { PixivApi.follow(user.id, add = next) }.onFailure { overrides[user.id] = !next; error = it.message }
		}
	}
}

// ---- tabs

class HomeViewModel : ViewModel() {
	enum class Section { RECOMMENDED, FOLLOWING, MANGA }
	var section by mutableStateOf(Section.RECOMMENDED)
	val recommended = IllustFeed { PixivApi.recommended() }
	val followed = IllustFeed { PixivApi.followIllusts() }
	val manga = IllustFeed { PixivApi.recommendedManga() }
	val feed: IllustFeed get() = when (section) { Section.RECOMMENDED -> recommended; Section.FOLLOWING -> followed; Section.MANGA -> manga }
	fun load(force: Boolean = false) = feed.load(viewModelScope, force)
	fun loadMore() = feed.loadMore(viewModelScope)
	fun update(i: Illust) { recommended.update(i); followed.update(i); manga.update(i) }
}

/** The chapters of a manga series. */
class SeriesViewModel(val seriesId: Long) : ViewModel() {
	var title by mutableStateOf("")
		private set
	var count by mutableStateOf(0)
		private set
	val illustFeed = IllustFeed { PixivApi.series(seriesId).let { com.mokona.app.data.IllustsPage(it.illusts, it.nextUrl) } }
	fun load(force: Boolean = false) {
		if (title.isEmpty()) viewModelScope.launch { runCatching { PixivApi.series(seriesId) }.onSuccess { title = it.detail.title; count = it.detail.workCount } }
		illustFeed.load(viewModelScope, force)
	}
	fun loadMore() = illustFeed.loadMore(viewModelScope)
	fun update(i: Illust) = illustFeed.update(i)
}

class RankingViewModel : ViewModel() {
	var mode by mutableStateOf(RankingMode.all.first())
		private set
	/** yyyy-MM-dd, or null for the latest ranking Pixiv has. */
	var date by mutableStateOf<String?>(null)
		private set
	private val feeds = HashMap<String, IllustFeed>()
	val feed: IllustFeed get() = feeds.getOrPut("${mode.id}@$date") { IllustFeed { PixivApi.ranking(mode.id, date) } }
	val modes: List<RankingMode> get() = RankingMode.all.filter { AppPrefs.showAdult || !it.adult }

	fun select(m: RankingMode) { mode = m; load() }
	fun selectDate(d: String?) { date = d; load() }
	fun load(force: Boolean = false) = feed.load(viewModelScope, force)
	fun loadMore() = feed.loadMore(viewModelScope)
	fun update(i: Illust) = feeds.values.forEach { it.update(i) }
}

class SearchViewModel : ViewModel() {
	/** The two halves of a search, as in the official app: what is new and what is popular for the same words. */
	enum class Section { NEWEST, POPULAR }

	var query by mutableStateOf("")
	var submitted by mutableStateOf("")
		private set
	/**
	 * The tag Pixiv matched to what was typed, when the words were a single tag. Typing "girl" resolves to 女の子
	 * with "girl" as its translation, so the header can show both and the search runs on the real tag.
	 */
	var submittedTag by mutableStateOf<Tag?>(null)
		private set
	var section by mutableStateOf(Section.NEWEST)
	var searchUsers by mutableStateOf(false)
	var sort by mutableStateOf(SearchSort.DATE_DESC)
	var target by mutableStateOf(SearchTarget.PARTIAL_TAGS)
	var duration by mutableStateOf(SearchDuration.ALL)
	var trending by mutableStateOf<List<TrendTag>>(emptyList())
		private set
	var suggestions by mutableStateOf<List<Tag>>(emptyList())
		private set
	var feed by mutableStateOf<IllustFeed?>(null)
		private set
	var popularFeed by mutableStateOf<IllustFeed?>(null)
		private set
	var userFeed by mutableStateOf<UserFeed?>(null)
		private set
	private var suggestJob: Job? = null

	fun loadTrending() {
		if (trending.isNotEmpty()) return
		viewModelScope.launch { runCatching { PixivApi.trendingTags() }.onSuccess { trending = it } }
	}

	/** The word suggestions were last asked for, so an answer that arrives late for an older word is dropped. */
	private var suggestedFor = ""

	/**
	 * Tag suggestions for the last word being typed, debounced, as a list under the field like Materixiv.
	 * Pixiv returns tags by popularity, so they are reordered: what starts with the typed word first (by its
	 * Japanese name or its translation), then what contains it, then the rest.
	 */
	fun onQueryChange(text: String) {
		query = text
		suggestJob?.cancel()
		val word = text.trimStart().substringAfterLast(' ').trim()
		suggestedFor = word
		if (word.isEmpty() || searchUsers) { suggestions = emptyList(); return }
		suggestJob = viewModelScope.launch {
			delay(250)
			val tags = runCatching { PixivApi.autocomplete(word) }.getOrNull() ?: return@launch
			if (suggestedFor != word) return@launch
			fun rank(t: Tag): Int {
				val names = listOfNotNull(t.name, t.translatedName)
				return when {
					names.any { it.equals(word, ignoreCase = true) } -> 0
					names.any { it.startsWith(word, ignoreCase = true) } -> 1
					names.any { it.contains(word, ignoreCase = true) } -> 2
					else -> 3
				}
			}
			suggestions = tags.distinctBy { it.name }.sortedBy(::rank)
		}
	}

	/** Replaces the word being typed with the chosen suggestion and searches it straight away. */
	fun pickSuggestion(tag: Tag) {
		val words = query.trim().split(Regex(" +")).toMutableList()
		if (words.isNotEmpty()) words.removeAt(words.lastIndex)
		words.add(tag.name)
		suggestions = emptyList()
		search(words.joinToString(" "), if (words.size == 1) tag else null)
	}

	/**
	 * Runs a search. With [known] the tag is already resolved (a suggestion, a chip in a work). Otherwise a single
	 * word is looked up first: if Pixiv knows it as the translation of a tag, the search uses that tag, so an
	 * English or Spanish word finds the Japanese-tagged works exactly like the official app does.
	 */
	fun search(word: String = query, known: Tag? = null) {
		val w = word.trim()
		if (w.isEmpty()) return
		suggestJob?.cancel(); suggestedFor = ""
		query = w; submitted = w; suggestions = emptyList(); submittedTag = known
		if (searchUsers) {
			feed = null; popularFeed = null
			userFeed = UserFeed { PixivApi.searchUsers(w) }.also { it.load(viewModelScope) }
			return
		}
		userFeed = null
		// Looked up once, shared by both sections; a failed lookup just searches the words as typed.
		val resolved = viewModelScope.async(start = CoroutineStart.LAZY) {
			if (known != null || w.contains(' ')) return@async known?.name ?: w
			val tags = runCatching { PixivApi.autocomplete(w) }.getOrDefault(emptyList())
			val tag = tags.firstOrNull { it.name.equals(w, ignoreCase = true) }
				?: tags.firstOrNull { it.translatedName?.equals(w, ignoreCase = true) == true }
			if (tag != null && submitted == w) submittedTag = tag
			tag?.name ?: w
		}
		feed = IllustFeed { PixivApi.search(resolved.await(), sort, target, duration) }.also { it.load(viewModelScope) }
		popularFeed = IllustFeed { PixivApi.searchPopular(resolved.await(), target, duration) }.also { it.load(viewModelScope) }
	}

	/** Re-runs the current search after a filter change. */
	fun refilter() { if (submitted.isNotEmpty() && !searchUsers) search(submitted, submittedTag) }

	fun clear() {
		suggestJob?.cancel(); suggestedFor = ""
		query = ""; submitted = ""; submittedTag = null; feed = null; popularFeed = null; userFeed = null; suggestions = emptyList()
	}
	fun loadMore() { feed?.loadMore(viewModelScope); popularFeed?.loadMore(viewModelScope); userFeed?.loadMore(viewModelScope) }
	fun update(i: Illust) { feed?.update(i); popularFeed?.update(i) }
}

/** Bookmarks tab: public, private, by tag, plus the browsing history. */
class BookmarksViewModel : ViewModel() {
	enum class Section { PUBLIC, PRIVATE, HISTORY, DOWNLOADS }
	var section by mutableStateOf(Section.PUBLIC)
		private set
	var tag by mutableStateOf<String?>(null)
		private set
	var tags by mutableStateOf<List<BookmarkTag>>(emptyList())
		private set
	private val feeds = HashMap<String, IllustFeed>()
	val feed: IllustFeed
		get() = feeds.getOrPut("$section@$tag") {
			IllustFeed {
				when (section) {
					Section.PUBLIC -> PixivApi.bookmarks(PixivAuth.userId, Restrict.PUBLIC, tag)
					Section.PRIVATE -> PixivApi.bookmarks(PixivAuth.userId, Restrict.PRIVATE, tag)
					Section.HISTORY -> PixivApi.history()
					Section.DOWNLOADS -> IllustsPage()
				}
			}
		}

	fun select(s: Section) { section = s; tag = null; if (s != Section.DOWNLOADS) { load(); loadTags() } }
	fun selectTag(t: String?) { tag = t; load() }
	fun load(force: Boolean = false) = feed.load(viewModelScope, force)
	fun loadMore() = feed.loadMore(viewModelScope)
	fun update(i: Illust) = feeds.values.forEach { it.update(i) }

	fun loadTags() {
		if (section == Section.HISTORY || section == Section.DOWNLOADS) { tags = emptyList(); return }
		val restrict = if (section == Section.PRIVATE) Restrict.PRIVATE else Restrict.PUBLIC
		viewModelScope.launch { runCatching { PixivApi.bookmarkTags(PixivAuth.userId, restrict) }.onSuccess { tags = it } }
	}

	/** After a bookmark toggle the lists here are stale; drop them so they reload on next view. */
	fun invalidate() { feeds.clear() }
}

/** An artist's page: profile, works, bookmarks, people they follow. */
class ArtistViewModel(val userId: Long) : ViewModel() {
	enum class Section { WORKS, MANGA, BOOKMARKS, FOLLOWING }
	var detail by mutableStateOf<UserDetail?>(null)
		private set
	var error by mutableStateOf<String?>(null)
		private set
	var section by mutableStateOf(Section.WORKS)
	val works = IllustFeed { PixivApi.userIllusts(userId, "illust") }
	val manga = IllustFeed { PixivApi.userIllusts(userId, "manga") }
	val bookmarks = IllustFeed { PixivApi.bookmarks(userId) }
	val following = UserFeed { PixivApi.following(userId) }

	fun load() {
		if (detail == null) viewModelScope.launch {
			runCatching { PixivApi.userDetail(userId) }.onSuccess { detail = it }.onFailure { error = it.message }
		}
		loadSection()
	}

	fun loadSection() {
		when (section) {
			Section.WORKS -> works.load(viewModelScope)
			Section.MANGA -> manga.load(viewModelScope)
			Section.BOOKMARKS -> bookmarks.load(viewModelScope)
			Section.FOLLOWING -> following.load(viewModelScope)
		}
	}

	fun reload() {
		when (section) {
			Section.WORKS -> works.load(viewModelScope, force = true)
			Section.MANGA -> manga.load(viewModelScope, force = true)
			Section.BOOKMARKS -> bookmarks.load(viewModelScope, force = true)
			Section.FOLLOWING -> following.load(viewModelScope, force = true)
		}
	}

	fun loadMore() {
		when (section) {
			Section.WORKS -> works.loadMore(viewModelScope)
			Section.MANGA -> manga.loadMore(viewModelScope)
			Section.BOOKMARKS -> bookmarks.loadMore(viewModelScope)
			Section.FOLLOWING -> following.loadMore(viewModelScope)
		}
	}

	fun update(i: Illust) { works.update(i); manga.update(i); bookmarks.update(i) }
}

// ---- one work

class DetailViewModel : ViewModel() {
	var illust by mutableStateOf<Illust?>(null)
		private set
	var related by mutableStateOf<List<Illust>>(emptyList())
		private set
	var comments by mutableStateOf<List<Comment>>(emptyList())
		private set
	var totalComments by mutableStateOf(0)
		private set
	private var commentsNext: String? = null
	var commentsLoading by mutableStateOf(false)
		private set
	/** Why the comments could not be read, shown in their place. Pixiv answers 404 when the author closed them. */
	var commentsError by mutableStateOf<String?>(null)
		private set
	var commentsClosed by mutableStateOf(false)
		private set
	/** A short notice at the bottom of the screen: failed action, saved file. */
	var message by mutableStateOf<String?>(null)
	var downloading by mutableStateOf(false)
		private set
	var encoding by mutableStateOf(false)
		private set
	var ugoira by mutableStateOf<UgoiraPlayer?>(null)
		private set

	fun open(base: Illust?, id: Long) {
		if (illust?.id == id) return
		illust = base
		related = emptyList(); comments = emptyList(); commentsNext = null; totalComments = 0
		commentsError = null; commentsClosed = false
		ugoira?.stop(); ugoira = null
		viewModelScope.launch {
			runCatching { PixivApi.detail(id) }.onSuccess { illust = it }.onFailure { if (base == null) message = it.message }
			val i = illust ?: return@launch
			if (i.isAnimated) ugoira = UgoiraPlayer(i.id, viewModelScope).also { it.start() }
			runCatching { PixivApi.related(id) }.onSuccess { related = it.illusts }
			loadComments()
		}
	}

	fun loadComments() {
		val i = illust ?: return
		if (commentsLoading) return
		val next = commentsNext
		if (comments.isNotEmpty() && next == null) return
		viewModelScope.launch {
			commentsLoading = true
			commentsError = null
			runCatching { if (next != null) PixivApi.nextComments(next) else PixivApi.comments(i.id) }
				.onSuccess { comments = comments + it.comments; commentsNext = it.nextUrl; totalComments = maxOf(totalComments, it.totalComments) }
				.onFailure { if ((it as? PixivException)?.code == 404) commentsClosed = true else commentsError = it.message }
			commentsLoading = false
		}
	}

	val hasMoreComments: Boolean get() = commentsNext != null

	fun addComment(text: String, onDone: () -> Unit) {
		val i = illust ?: return
		val t = text.trim()
		if (t.isEmpty()) return
		viewModelScope.launch {
			runCatching { PixivApi.addComment(i.id, t) }
				.onSuccess {
					comments = emptyList(); commentsNext = null
					loadComments(); onDone()
				}
				.onFailure { message = it.message }
		}
	}

	/** Tap: add or remove a public bookmark. Long press: add a private one. */
	fun toggleBookmark(onChanged: (Illust) -> Unit, restrict: Restrict = Restrict.PUBLIC) {
		val i = illust ?: return
		val add = if (restrict == Restrict.PRIVATE) true else !i.isBookmarked
		if (add && i.isBookmarked && restrict == Restrict.PRIVATE) return
		val next = i.copy(isBookmarked = add, totalBookmarks = i.totalBookmarks + if (add) 1 else -1)
		illust = next
		onChanged(next)
		viewModelScope.launch {
			runCatching { PixivApi.bookmark(i.id, add = add, restrict = restrict) }.onFailure {
				illust = i; onChanged(i); message = it.message
			}
		}
	}

	/** Saves every page of the work in Pictures/Mokona through MediaStore (no storage permission needed). */
	fun download(onDone: (List<Uri>) -> Unit) {
		val i = illust ?: return
		if (downloading) return
		viewModelScope.launch {
			downloading = true
			val saved = ArrayList<Uri>()
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
						uri
					}
				}.onSuccess { saved.add(it) }.onFailure { message = MokonaApp.appContext.getString(R.string.download_failed, index + 1, it.message ?: it.toString()) }
			}
			downloading = false
			onDone(saved)
		}
	}

	/** Encodes the ugoira to MP4 in Movies/Mokona with the phone's own H.264 encoder. */
	fun downloadVideo(onDone: (Uri) -> Unit) {
		val i = illust ?: return
		if (encoding) return
		viewModelScope.launch {
			encoding = true
			runCatching {
				val meta = ugoira?.meta ?: PixivApi.ugoira(i.id)
				val files = ugoira?.files ?: withContext(Dispatchers.IO) { UgoiraPlayer.unzip(PixivApi.download(meta.zipUrls.medium)) }
				withContext(Dispatchers.Default) { UgoiraVideo.encode(MokonaApp.appContext, i.id, meta, files) }
			}.onSuccess(onDone).onFailure { message = it.message ?: it.toString() }
			encoding = false
		}
	}

	override fun onCleared() { ugoira?.stop() }
}

/**
 * Plays a ugoira: downloads the frame zip once, then decodes frame after frame
 * at the delay Pixiv gives. Frames are decoded on demand so long animations
 * do not need every bitmap in memory at once.
 */
class UgoiraPlayer(private val illustId: Long, private val scope: CoroutineScope) {
	var frame by mutableStateOf<Bitmap?>(null)
		private set
	var progress by mutableStateOf(0f)
		private set
	var error by mutableStateOf<String?>(null)
		private set
	var playing by mutableStateOf(true)
	private var job: Job? = null
	/** Frame list and zip contents once loaded; the video export reuses them. */
	var meta: UgoiraMetadata? = null
		private set
	var files: Map<String, ByteArray>? = null
		private set

	fun start() {
		job = scope.launch {
			val meta: UgoiraMetadata
			val files: Map<String, ByteArray>
			try {
				meta = PixivApi.ugoira(illustId)
				val zip = PixivApi.download(meta.zipUrls.medium)
				files = withContext(Dispatchers.IO) { unzip(zip) }
			} catch (e: Exception) {
				error = e.message; return@launch
			}
			this@UgoiraPlayer.meta = meta
			this@UgoiraPlayer.files = files
			if (meta.frames.isEmpty()) { error = "no frames"; return@launch }
			val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
			var index = 0
			while (isActive) {
				if (!playing) { delay(100); continue }
				val f = meta.frames[index]
				val bytes = files[f.file]
				if (bytes != null) {
					val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
					if (bmp != null) frame = bmp
				}
				progress = (index + 1f) / meta.frames.size
				delay(f.delay.coerceAtLeast(16).toLong())
				index = (index + 1) % meta.frames.size
			}
		}
	}

	fun stop() { job?.cancel() }

	companion object {
		fun unzip(bytes: ByteArray): Map<String, ByteArray> {
			val out = HashMap<String, ByteArray>()
			ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
				var e = z.nextEntry
				while (e != null) {
					if (!e.isDirectory) out[e.name] = z.readBytes()
					e = z.nextEntry
				}
			}
			return out
		}
	}
}

// ---- login and links

/** The OAuth code that came back from the browser, waiting for the login screen to use it. */
object LoginBridge {
	var pendingCode by mutableStateOf<String?>(null)
}

/** A pixiv.net link opened with Mokona, waiting for the navigation to show it. */
object LinkBridge {
	var pendingIllust by mutableStateOf<Long?>(null)
	var pendingUser by mutableStateOf<Long?>(null)
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
