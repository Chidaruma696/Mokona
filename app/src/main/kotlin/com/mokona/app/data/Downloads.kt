package com.mokona.app.data

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mokona.app.MokonaApp
import com.mokona.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** A work kept on the phone: its data as Pixiv gave it and the original file of every page. */
@Serializable
data class DownloadedWork(val illust: Illust, val files: List<String>, val savedAt: Long) {
	/** The same work, but every picture points at the local file, so the detail and the reader work offline. */
	fun offline(): Illust {
		val uris = files.map { "file://$it" }
		val first = uris.firstOrNull() ?: return illust
		val local = ImageUrls(squareMedium = illust.imageUrls.squareMedium, medium = first, large = first, original = first)
		return illust.copy(
			imageUrls = local,
			metaSinglePage = MetaSinglePage(first),
			metaPages = if (uris.size > 1) uris.map { MetaPage(ImageUrls(squareMedium = it, medium = it, large = it, original = it)) } else emptyList(),
		)
	}
}

/** The works saved for reading offline: an index file and one folder per work in the app's storage. */
object DownloadRepository {
	private val json = Json { ignoreUnknownKeys = true }
	val works = mutableStateListOf<DownloadedWork>()
	private lateinit var root: File
	private val index: File get() = File(root, "index.json")

	fun init(context: Context) {
		root = File(context.getExternalFilesDir(null) ?: context.filesDir, "works").apply { mkdirs() }
		runCatching { json.decodeFromString(ListSerializer(DownloadedWork.serializer()), index.readText()) }.getOrNull()?.let { works.addAll(it) }
	}

	fun has(id: Long) = works.any { it.illust.id == id }
	fun get(id: Long) = works.find { it.illust.id == id }
	fun folder(id: Long): File = File(root, id.toString()).apply { mkdirs() }

	@Synchronized
	fun save(work: DownloadedWork) {
		works.removeAll { it.illust.id == work.illust.id }
		works.add(0, work)
		index.writeText(json.encodeToString(ListSerializer(DownloadedWork.serializer()), works.toList()))
	}

	@Synchronized
	fun remove(id: Long) {
		works.removeAll { it.illust.id == id }
		File(root, id.toString()).deleteRecursively()
		index.writeText(json.encodeToString(ListSerializer(DownloadedWork.serializer()), works.toList()))
	}

	fun sizeBytes(): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}

/** What the download service is doing, for the bar at the bottom of the app. */
object DownloadQueue {
	val pending = mutableStateListOf<Illust>()
	var current by mutableStateOf<Illust?>(null)
		internal set
	var done by mutableIntStateOf(0)
		internal set
	var total by mutableIntStateOf(0)
		internal set
	var lastError by mutableStateOf<String?>(null)
		internal set

	val active: Boolean get() = current != null || pending.isNotEmpty()

	fun enqueue(context: Context, illust: Illust) {
		if (pending.any { it.id == illust.id } || current?.id == illust.id || DownloadRepository.has(illust.id)) return
		pending.add(illust)
		TranslationModels.ensureChannel(context)
		ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
	}
}

/**
 * Foreground service that saves works page by page: a notification with the real progress
 * (pages done of total), the bar in the app fed from DownloadQueue, and the index updated when a
 * work is complete. Stops itself when the queue is empty.
 */
class DownloadService : Service() {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var job: Job? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		ServiceCompat.startForeground(
			this, NOTIFICATION, notification(getString(R.string.downloads_channel), null, 0, 0),
			if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
		)
		if (job?.isActive != true) job = scope.launch { drain(); stopSelf() }
		return START_NOT_STICKY
	}

	private suspend fun drain() {
		while (true) {
			val illust = withContext(Dispatchers.Main) { DownloadQueue.pending.removeFirstOrNull() } ?: break
			withContext(Dispatchers.Main) { DownloadQueue.current = illust; DownloadQueue.done = 0; DownloadQueue.total = illust.originalUrls.size; DownloadQueue.lastError = null }
			try {
				val folder = DownloadRepository.folder(illust.id)
				val files = illust.originalUrls.mapIndexed { i, url ->
					val ext = url.substringAfterLast('.', "jpg").take(4)
					val file = File(folder, "p$i.$ext")
					if (!file.exists() || file.length() == 0L) file.writeBytes(PixivApi.download(url))
					withContext(Dispatchers.Main) { DownloadQueue.done = i + 1 }
					notify(illust.title, i + 1, illust.originalUrls.size)
					file.absolutePath
				}
				DownloadRepository.save(DownloadedWork(illust, files, System.currentTimeMillis()))
			} catch (e: Exception) {
				withContext(Dispatchers.Main) { DownloadQueue.lastError = "${illust.title}: ${e.message ?: e.javaClass.simpleName}" }
			}
		}
		withContext(Dispatchers.Main) { DownloadQueue.current = null }
		val nm = NotificationManagerCompat.from(this)
		runCatching { nm.notify(NOTIFICATION_DONE, notification(getString(R.string.downloads_done), null, 0, 0, done2 = true)) }
	}

	private fun notify(title: String, done: Int, total: Int) {
		runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION, notification(getString(R.string.saving_work, title), "$done/$total", done, total)) }
	}

	private fun notification(title: String, text: String?, done: Int, total: Int, done2: Boolean = false): Notification =
		NotificationCompat.Builder(this, TranslationModels.CHANNEL)
			.setSmallIcon(if (done2) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
			.setContentTitle(title).setContentText(text).setOngoing(!done2).setAutoCancel(done2).setOnlyAlertOnce(true)
			.apply { if (!done2) setProgress(total, done, total == 0) }
			.build()

	override fun onDestroy() {
		scope.cancel()
		super.onDestroy()
	}

	companion object {
		private const val NOTIFICATION = 42
		private const val NOTIFICATION_DONE = 43
	}
}
