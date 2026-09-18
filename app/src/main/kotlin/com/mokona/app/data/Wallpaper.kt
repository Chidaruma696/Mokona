package com.mokona.app.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mokona.app.MokonaApp
import com.mokona.app.ui.AppPrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/** A work the user put in a wallpaper list: enough to show it and to fetch its original later. */
@Serializable
data class WallpaperItem(val id: Long, val title: String, val artist: String, val thumb: String)

@Serializable
data class WallpaperList(val id: Long, val name: String, val items: List<WallpaperItem> = emptyList())

/**
 * Your Pixiv on the phone's home screen. Every so often a background job picks a work from the
 * chosen source (random recommendations, what you follow, your bookmarks, or a list you made),
 * downloads the original and sets it as wallpaper. Never adult works, never manga pages.
 */
object WallpaperPrefs {
	private const val FILE = "wallpaper"
	private const val WORK = "wallpaper-rotation"
	private const val NOW = "wallpaper-now"
	private val json = Json { ignoreUnknownKeys = true }

	enum class Source { RECOMMENDED, FOLLOWING, BOOKMARKS, LIST }
	enum class Target { HOME, LOCK, BOTH }
	val intervals = listOf(30, 60, 180, 360, 720, 1440)

	var enabled by mutableStateOf(false)
		private set
	var source by mutableStateOf(Source.RECOMMENDED)
		private set
	var listId by mutableStateOf(0L)
		private set
	var intervalMinutes by mutableStateOf(180)
		private set
	var target by mutableStateOf(Target.BOTH)
		private set
	var wifiOnly by mutableStateOf(true)
		private set
	var lastTitle by mutableStateOf<String?>(null)
		private set
	var lastError by mutableStateOf<String?>(null)
		private set
	var lastChange by mutableStateOf(0L)
		private set
	val lists = mutableStateListOf<WallpaperList>()
	/** Ids shown lately, so the same picture does not come back right away. */
	private var recent: MutableList<Long> = mutableListOf()

	fun init(context: Context) {
		val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		enabled = p.getBoolean("enabled", false)
		source = runCatching { Source.valueOf(p.getString("source", "RECOMMENDED")!!) }.getOrDefault(Source.RECOMMENDED)
		listId = p.getLong("list_id", 0L)
		intervalMinutes = p.getInt("interval", 180)
		target = runCatching { Target.valueOf(p.getString("target", "BOTH")!!) }.getOrDefault(Target.BOTH)
		wifiOnly = p.getBoolean("wifi_only", true)
		lastTitle = p.getString("last_title", null)
		lastError = p.getString("last_error", null)
		lastChange = p.getLong("last_change", 0L)
		recent = p.getString("recent", "")!!.split(",").mapNotNull { it.toLongOrNull() }.toMutableList()
		lists.clear()
		runCatching { json.decodeFromString(ListSerializer(WallpaperList.serializer()), p.getString("lists", "[]")!!) }.getOrNull()?.let { lists.addAll(it) }
	}

	private fun prefs() = MokonaApp.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
	private fun saveLists() { prefs().edit { putString("lists", json.encodeToString(ListSerializer(WallpaperList.serializer()), lists.toList())) } }

	fun updateEnabled(context: Context, value: Boolean) { enabled = value; prefs().edit { putBoolean("enabled", value) }; schedule(context) }
	fun updateSource(value: Source) { source = value; prefs().edit { putString("source", value.name) } }
	fun updateListId(value: Long) { listId = value; prefs().edit { putLong("list_id", value) } }
	fun updateInterval(context: Context, minutes: Int) { intervalMinutes = minutes; prefs().edit { putInt("interval", minutes) }; schedule(context) }
	fun updateTarget(value: Target) { target = value; prefs().edit { putString("target", value.name) } }
	fun updateWifiOnly(context: Context, value: Boolean) { wifiOnly = value; prefs().edit { putBoolean("wifi_only", value) }; schedule(context) }

	fun createList(name: String): WallpaperList {
		val list = WallpaperList(id = System.currentTimeMillis(), name = name.trim().ifBlank { "Lista" })
		lists.add(list); saveLists()
		return list
	}

	fun renameList(id: Long, name: String) { replace(id) { it.copy(name = name.trim().ifBlank { it.name }) } }
	fun deleteList(id: Long) { lists.removeAll { it.id == id }; saveLists(); if (listId == id) updateListId(0L) }
	fun addToList(id: Long, item: WallpaperItem): Boolean {
		val list = lists.find { it.id == id } ?: return false
		if (list.items.any { it.id == item.id }) return false
		replace(id) { it.copy(items = it.items + item) }
		return true
	}
	fun removeFromList(id: Long, illustId: Long) { replace(id) { it.copy(items = it.items.filter { i -> i.id != illustId }) } }
	private fun replace(id: Long, f: (WallpaperList) -> WallpaperList) {
		val i = lists.indexOfFirst { it.id == id }
		if (i >= 0) { lists[i] = f(lists[i]); saveLists() }
	}

	/** (Re)programs the periodic job, or cancels it when the feature is off. */
	fun schedule(context: Context) {
		val wm = WorkManager.getInstance(context)
		if (!enabled) { wm.cancelUniqueWork(WORK); return }
		val constraints = Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
		val request = PeriodicWorkRequestBuilder<WallpaperWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES).setConstraints(constraints).build()
		wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
	}

	fun changeNow(context: Context) {
		WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WallpaperWorker>().build())
	}

	internal fun remember(id: Long) {
		recent.add(id)
		while (recent.size > 30) recent.removeAt(0)
		prefs().edit { putString("recent", recent.joinToString(",")) }
	}
	internal fun seenLately(id: Long) = id in recent
	internal fun report(title: String?, error: String?) {
		lastTitle = title; lastError = error; lastChange = System.currentTimeMillis()
		prefs().edit { putString("last_title", title); putString("last_error", error); putLong("last_change", lastChange) }
	}
}

class WallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
	override suspend fun doWork(): Result {
		if (!PixivAuth.isLoggedIn) return Result.success()
		return try {
			val illust = pick() ?: run { WallpaperPrefs.report(null, "empty"); return Result.success() }
			val url = illust.originalUrls.firstOrNull() ?: illust.imageUrls.large
			val bytes = PixivApi.download(url)
			val metrics: DisplayMetrics = applicationContext.resources.displayMetrics
			val bitmap = decodeFor(bytes, metrics.widthPixels, metrics.heightPixels) ?: throw IllegalStateException("decode")
			val flags = when (WallpaperPrefs.target) {
				WallpaperPrefs.Target.HOME -> WallpaperManager.FLAG_SYSTEM
				WallpaperPrefs.Target.LOCK -> WallpaperManager.FLAG_LOCK
				WallpaperPrefs.Target.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
			}
			WallpaperManager.getInstance(applicationContext).setBitmap(bitmap, null, true, flags)
			WallpaperPrefs.remember(illust.id)
			WallpaperPrefs.report("${illust.title} · ${illust.user.name}", null)
			Result.success()
		} catch (e: Exception) {
			WallpaperPrefs.report(WallpaperPrefs.lastTitle, e.message ?: e.javaClass.simpleName)
			if (runAttemptCount < 2) Result.retry() else Result.failure()
		}
	}

	/** A random safe illustration from the chosen source, avoiding the ones shown lately. */
	private suspend fun pick(): Illust? {
		val candidates: List<Illust> = when (WallpaperPrefs.source) {
			WallpaperPrefs.Source.RECOMMENDED -> PixivApi.recommended().illusts
			WallpaperPrefs.Source.FOLLOWING -> PixivApi.followIllusts().illusts
			WallpaperPrefs.Source.BOOKMARKS -> PixivApi.bookmarks(PixivAuth.userId).illusts
			WallpaperPrefs.Source.LIST -> {
				val list = WallpaperPrefs.lists.find { it.id == WallpaperPrefs.listId } ?: return null
				val fresh = list.items.filterNot { WallpaperPrefs.seenLately(it.id) }.ifEmpty { list.items }
				val item = fresh.randomOrNull() ?: return null
				return PixivApi.detail(item.id)
			}
		}
		val safe = candidates.filter { !it.isAdult && !it.isManga && !it.isAnimated }
		return (safe.filterNot { WallpaperPrefs.seenLately(it.id) }.ifEmpty { safe }).randomOrNull()
	}

	private fun decodeFor(bytes: ByteArray, w: Int, h: Int): android.graphics.Bitmap? {
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
		var sample = 1
		while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
		return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
	}
}
