package com.mokona.app.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Pixiv's app API, the same one the official Android app talks to. */
object PixivApi {
	const val BASE = "https://app-api.pixiv.net/"
	const val IMAGE_REFERER = "https://app-api.pixiv.net/"
	const val APP_VERSION = "6.101.1"
	private const val HASH_SALT = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"
	val userAgent: String get() = "PixivAndroidApp/$APP_VERSION (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

	private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
	val client: OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
		.build()

	private fun clientHash(time: String): String =
		MessageDigest.getInstance("MD5").digest((time + HASH_SALT).toByteArray()).joinToString("") { "%02x".format(it) }

	private fun request(url: HttpUrl, token: String, body: FormBody? = null): Request {
		val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
		val b = Request.Builder().url(url)
			.header("User-Agent", userAgent)
			.header("App-OS", "android")
			.header("App-OS-Version", Build.VERSION.RELEASE)
			.header("App-Version", APP_VERSION)
			.header("Accept-Language", Locale.getDefault().toLanguageTag())
			.header("X-Client-Time", time)
			.header("X-Client-Hash", clientHash(time))
			.header("Authorization", "Bearer $token")
		if (body != null) b.post(body)
		return b.build()
	}

	/** GET/POST with one automatic token refresh on 400/401. */
	private suspend fun call(url: HttpUrl, body: FormBody? = null): String = withContext(Dispatchers.IO) {
		var token = PixivAuth.validToken()
		repeat(2) { attempt ->
			client.newCall(request(url, token, body)).execute().use { r ->
				val text = r.body.string()
				if (r.isSuccessful) return@withContext text
				if ((r.code == 400 || r.code == 401) && attempt == 0) {
					token = PixivAuth.forceRefresh()
				} else {
					throw PixivException(r.code, text.take(300))
				}
			}
		}
		throw PixivException(0, "unreachable")
	}

	private fun url(path: String, vararg params: Pair<String, String?>): HttpUrl {
		val b = (BASE + path).toHttpUrl().newBuilder().addQueryParameter("filter", "for_android")
		for ((k, v) in params) if (v != null) b.addQueryParameter(k, v)
		return b.build()
	}

	private fun form(vararg fields: Pair<String, String?>): FormBody {
		val b = FormBody.Builder()
		for ((k, v) in fields) if (v != null) b.add(k, v)
		return b.build()
	}

	private suspend fun illusts(url: HttpUrl): IllustsPage = json.decodeFromString(call(url))
	private suspend fun users(url: HttpUrl): UsersPage = json.decodeFromString(call(url))

	// ---- works

	suspend fun recommended(): IllustsPage =
		illusts(url("v1/illust/recommended", "include_ranking_illusts" to "true", "include_privacy_policy" to "true"))

	/** New works from the people the user follows. */
	suspend fun followIllusts(restrict: Restrict = Restrict.PUBLIC): IllustsPage =
		illusts(url("v2/illust/follow", "restrict" to restrict.id))

	suspend fun ranking(mode: String, date: String? = null): IllustsPage =
		illusts(url("v1/illust/ranking", "mode" to mode, "date" to date))

	suspend fun search(
		word: String,
		sort: SearchSort = SearchSort.DATE_DESC,
		target: SearchTarget = SearchTarget.PARTIAL_TAGS,
		duration: SearchDuration = SearchDuration.ALL,
	): IllustsPage = illusts(
		url(
			"v1/search/illust",
			"word" to word, "search_target" to target.id, "sort" to sort.id, "duration" to duration.id,
			"merge_plain_keyword_results" to "true", "include_translated_tag_results" to "true",
		),
	)

	suspend fun related(illustId: Long): IllustsPage = illusts(url("v2/illust/related", "illust_id" to illustId.toString()))

	suspend fun userIllusts(userId: Long, type: String = "illust"): IllustsPage =
		illusts(url("v1/user/illusts", "user_id" to userId.toString(), "type" to type))

	suspend fun bookmarks(userId: Long, restrict: Restrict = Restrict.PUBLIC, tag: String? = null): IllustsPage =
		illusts(url("v1/user/bookmarks/illust", "user_id" to userId.toString(), "restrict" to restrict.id, "tag" to tag))

	suspend fun bookmarkTags(userId: Long, restrict: Restrict = Restrict.PUBLIC): List<BookmarkTag> =
		json.decodeFromString<BookmarkTagsPage>(call(url("v1/user/bookmark-tags/illust", "user_id" to userId.toString(), "restrict" to restrict.id))).bookmarkTags

	suspend fun history(): IllustsPage = illusts(url("v1/user/browsing-history/illusts"))

	/** The next page of any list of works: Pixiv hands back the full URL. */
	suspend fun nextPage(nextUrl: String): IllustsPage = illusts(nextUrl.toHttpUrl())

	suspend fun detail(illustId: Long): Illust =
		json.decodeFromString<IllustDetail>(call(url("v1/illust/detail", "illust_id" to illustId.toString()))).illust

	suspend fun trendingTags(): List<TrendTag> =
		json.decodeFromString<TrendTags>(call(url("v1/trending-tags/illust"))).trendTags

	suspend fun autocomplete(word: String): List<Tag> =
		json.decodeFromString<AutocompleteResponse>(
			call(url("v2/search/autocomplete", "word" to word, "merge_plain_keyword_results" to "true", "include_translated_tag_results" to "true")),
		).tags

	suspend fun bookmark(illustId: Long, add: Boolean, restrict: Restrict = Restrict.PUBLIC) {
		if (add) call(url("v2/illust/bookmark/add"), form("illust_id" to illustId.toString(), "restrict" to restrict.id))
		else call(url("v1/illust/bookmark/delete"), form("illust_id" to illustId.toString()))
	}

	suspend fun ugoira(illustId: Long): UgoiraMetadata =
		json.decodeFromString<UgoiraResponse>(call(url("v1/ugoira/metadata", "illust_id" to illustId.toString()))).ugoiraMetadata

	// ---- comments

	suspend fun comments(illustId: Long): CommentsPage =
		json.decodeFromString(call(url("v1/illust/comments", "illust_id" to illustId.toString())))

	suspend fun nextComments(nextUrl: String): CommentsPage = json.decodeFromString(call(nextUrl.toHttpUrl()))

	suspend fun addComment(illustId: Long, text: String, parentId: Long? = null) {
		call(url("v1/illust/comment/add"), form("illust_id" to illustId.toString(), "comment" to text, "parent_comment_id" to parentId?.toString()))
	}

	// ---- people

	suspend fun userDetail(userId: Long): UserDetail =
		json.decodeFromString(call(url("v1/user/detail", "user_id" to userId.toString())))

	suspend fun follow(userId: Long, add: Boolean, restrict: Restrict = Restrict.PUBLIC) {
		if (add) call(url("v1/user/follow/add"), form("user_id" to userId.toString(), "restrict" to restrict.id))
		else call(url("v1/user/follow/delete"), form("user_id" to userId.toString()))
	}

	suspend fun following(userId: Long, restrict: Restrict = Restrict.PUBLIC): UsersPage =
		users(url("v1/user/following", "user_id" to userId.toString(), "restrict" to restrict.id))

	suspend fun followers(userId: Long): UsersPage = users(url("v1/user/follower", "user_id" to userId.toString()))

	suspend fun recommendedUsers(): UsersPage = users(url("v1/user/recommended"))

	suspend fun searchUsers(word: String): UsersPage = users(url("v1/search/user", "word" to word))

	suspend fun nextUsers(nextUrl: String): UsersPage = users(nextUrl.toHttpUrl())

	// ---- files

	/** Downloads an original file or a ugoira zip (needs the Pixiv referer). */
	suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
		val r = client.newCall(Request.Builder().url(url).header("Referer", IMAGE_REFERER).header("User-Agent", userAgent).build()).execute()
		r.use { if (!it.isSuccessful) throw PixivException(it.code, "download"); it.body.bytes() }
	}
}
