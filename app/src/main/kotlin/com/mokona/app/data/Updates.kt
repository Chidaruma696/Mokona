package com.mokona.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.mokona.app.BuildConfig
import com.mokona.app.MokonaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * New versions come from GitHub releases. Once a day at start (and on demand from Settings) Mokona
 * asks for the latest release and, if its tag is newer than this build, offers the APK.
 */
object Updates {
	private const val FILE = "updates"
	private const val LATEST = "https://api.github.com/repos/Chidaruma696/Mokona/releases/latest"
	private const val DAY = 24L * 60 * 60 * 1000
	private val json = Json { ignoreUnknownKeys = true }

	@Serializable
	data class Asset(val name: String = "", @SerialName("browser_download_url") val url: String = "")

	@Serializable
	data class Release(
		@SerialName("tag_name") val tag: String = "",
		@SerialName("html_url") val pageUrl: String = "",
		val body: String = "",
		val assets: List<Asset> = emptyList(),
	) {
		val version: String get() = tag.removePrefix("v")
		/** The APK built for this phone's architecture (releases ship one per ABI), or any APK. */
		val apkUrl: String? get() = apkUrlFor(android.os.Build.SUPPORTED_ABIS?.toList().orEmpty())

		/** `abis` in the phone's order of preference, as `Build.SUPPORTED_ABIS` lists them. */
		fun apkUrlFor(abis: List<String>): String? {
			val apks = assets.filter { it.name.endsWith(".apk") }
			val abi = abis.firstOrNull { a -> apks.any { it.name.contains(a) } }
			return (apks.firstOrNull { abi != null && it.name.contains(abi) } ?: apks.firstOrNull())?.url
		}
	}

	var available by mutableStateOf<Release?>(null)
		private set
	var checking by mutableStateOf(false)
		private set
	var lastResult by mutableStateOf<String?>(null)
		private set
	private var lastCheck = 0L
	private var skipped = ""

	fun init(context: Context) {
		val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		lastCheck = p.getLong("last_check", 0L)
		skipped = p.getString("skipped", "") ?: ""
	}

	private fun prefs() = MokonaApp.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	/** Daily automatic check; `force` ignores the day and the skipped version. */
	suspend fun check(force: Boolean = false): Release? {
		if (!force && System.currentTimeMillis() - lastCheck < DAY) return available
		checking = true
		try {
			val release = withContext(Dispatchers.IO) {
				PixivApi.client.newCall(Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
					if (!r.isSuccessful) throw IllegalStateException("GitHub ${r.code}")
					json.decodeFromString<Release>(r.body.string())
				}
			}
			lastCheck = System.currentTimeMillis()
			prefs().edit { putLong("last_check", lastCheck) }
			val newer = isNewer(release.version, BuildConfig.VERSION_NAME)
			available = if (newer && (force || release.version != skipped)) release else null
			lastResult = if (newer) release.version else ""
			return available
		} catch (e: Exception) {
			lastResult = e.message ?: e.javaClass.simpleName
			return null
		} finally {
			checking = false
		}
	}

	fun skip(version: String) { skipped = version; prefs().edit { putString("skipped", version) }; available = null }
	fun dismiss() { available = null }

	/** "0.2.0" > "0.1.10": part by part, numbers first, any suffix loses. */
	fun isNewer(candidate: String, current: String): Boolean {
		fun parts(v: String) = v.split(".", "-").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
		val a = parts(candidate); val b = parts(current)
		for (i in 0 until maxOf(a.size, b.size)) {
			val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
			if (x != y) return x > y
		}
		return false
	}
}
