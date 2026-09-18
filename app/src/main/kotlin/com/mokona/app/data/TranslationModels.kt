package com.mokona.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.mokona.app.MokonaApp
import com.mokona.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * The optional offline dictionaries. ML Kit keeps one model per language (each translates to and
 * from English), so Japanese → Spanish needs the Japanese and the Spanish models. With them on the
 * phone the translator works with no connection at all. This object knows what is downloaded,
 * downloads from Settings (or from the first 訳 in offline mode) and shows a bar and a notification
 * while it does; ML Kit gives no percentage, so both are indeterminate.
 */
object TranslationModels {
	const val CHANNEL = "downloads"
	private const val NOTIFICATION = 41
	private val manager = RemoteModelManager.getInstance()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	val downloaded = mutableStateListOf<String>()
	val downloading = mutableStateMapOf<String, Boolean>()
	var lastError by mutableStateOf<String?>(null)
		private set
	/** Flips to true when a notification is wanted and Android 13+ has not allowed them yet; the UI asks once. */
	var wantsPermission by mutableStateOf(false)

	fun refresh() {
		scope.launch {
			runCatching { manager.getDownloadedModels(TranslateRemoteModel::class.java).await() }
				.onSuccess { models -> downloaded.clear(); downloaded.addAll(models.map { it.language }) }
		}
	}

	/** Models a source needs to translate into the current target; English is built in. */
	fun needed(source: PageTranslator.Source): List<String> = listOf(source.tag, PageTranslator.target()).distinct().filter { it != TranslateLanguage.ENGLISH }

	fun ready(source: PageTranslator.Source): Boolean = needed(source).all { it in downloaded }

	fun name(tag: String): String = Locale(tag).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }

	suspend fun download(source: PageTranslator.Source) {
		for (tag in needed(source)) if (tag !in downloaded) download(tag)
	}

	suspend fun download(tag: String) {
		if (tag in downloaded || downloading[tag] == true) return
		downloading[tag] = true
		lastError = null
		notify(MokonaApp.appContext, tag, done = false)
		try {
			manager.download(TranslateRemoteModel.Builder(tag).build(), DownloadConditions.Builder().build()).await()
			if (tag !in downloaded) downloaded.add(tag)
			notify(MokonaApp.appContext, tag, done = true)
		} catch (e: Exception) {
			lastError = e.message ?: e.javaClass.simpleName
			NotificationManagerCompat.from(MokonaApp.appContext).cancel(NOTIFICATION)
			throw e
		} finally {
			downloading.remove(tag)
		}
	}

	fun downloadInBackground(tag: String) { scope.launch { runCatching { download(tag) } } }

	fun delete(tag: String) {
		scope.launch {
			runCatching { manager.deleteDownloadedModel(TranslateRemoteModel.Builder(tag).build()).await() }
			downloaded.remove(tag)
		}
	}

	/** True when the app may post notifications; otherwise asks the UI to request them. */
	fun ensureChannel(context: Context): Boolean {
		if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			wantsPermission = true
			return false
		}
		val nm = NotificationManagerCompat.from(context)
		if (!nm.areNotificationsEnabled()) return false
		if (Build.VERSION.SDK_INT >= 26) {
			nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW))
		}
		return true
	}

	private fun notify(context: Context, tag: String, done: Boolean) {
		if (!ensureChannel(context)) return
		val n = NotificationCompat.Builder(context, CHANNEL)
			.setSmallIcon(if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
			.setContentTitle(if (done) context.getString(R.string.model_ready, name(tag)) else context.getString(R.string.model_downloading, name(tag)))
			.setContentText(if (done) context.getString(R.string.model_ready_body) else context.getString(R.string.model_downloading_body))
			.setOngoing(!done).setAutoCancel(done).setOnlyAlertOnce(true)
			.apply { if (!done) setProgress(0, 0, true) }
			.build()
		runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION, n) }
	}
}
