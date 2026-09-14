package com.mokona.app.data

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.mokona.app.MokonaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pixiv's OAuth for its own Android app: the web login page hands back a code
 * through a pixiv:// link, and PKCE proves it was this app that asked. Tokens
 * live in app-private preferences and refresh themselves an hour later.
 */
object PixivAuth {
	private const val FILE = "auth"
	private const val OAUTH_URL = "https://oauth.secure.pixiv.net/auth/token"
	const val LOGIN_URL = "https://app-api.pixiv.net/web/v1/login"
	private const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"
	// The public client of Pixiv's Android app; every third-party client uses the same pair.
	private const val CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT"
	private const val CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"

	private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
	private val http = OkHttpClient()
	private val refreshLock = Mutex()

	var accessToken: String? = null
		private set
	private var refreshToken: String? = null
	private var expiresAt: Long = 0
	var userName by mutableStateOf<String?>(null)
		private set
	var userId by mutableStateOf(0L)
		private set
	val isLoggedIn: Boolean get() = userName != null

	fun init(context: Context) {
		val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		accessToken = p.getString("access", null)
		refreshToken = p.getString("refresh", null)
		expiresAt = p.getLong("expires_at", 0)
		userName = p.getString("user_name", null)
		userId = p.getLong("user_id", 0)
	}

	private fun prefs() = MokonaApp.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	// ---- PKCE
	private var verifier: String = ""

	/** Starts a login attempt: returns the URL the WebView must open. */
	fun beginLogin(): String {
		val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
		verifier = base64Url(bytes)
		val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
		return "$LOGIN_URL?code_challenge=$challenge&code_challenge_method=S256&client=pixiv-android"
	}

	private fun base64Url(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

	/** The code from the pixiv://account/login?code=… redirect. */
	suspend fun finishLogin(code: String) = withContext(Dispatchers.IO) {
		val form = FormBody.Builder()
			.add("client_id", CLIENT_ID).add("client_secret", CLIENT_SECRET)
			.add("grant_type", "authorization_code").add("code", code)
			.add("code_verifier", verifier).add("redirect_uri", REDIRECT_URI)
			.add("include_policy", "true")
			.build()
		exchange(form)
	}

	private suspend fun refresh() = withContext(Dispatchers.IO) {
		val token = refreshToken ?: throw IllegalStateException("not logged in")
		val form = FormBody.Builder()
			.add("client_id", CLIENT_ID).add("client_secret", CLIENT_SECRET)
			.add("grant_type", "refresh_token").add("refresh_token", token)
			.add("include_policy", "true")
			.build()
		exchange(form)
	}

	private fun exchange(form: FormBody) {
		val request = Request.Builder().url(OAUTH_URL).post(form)
			.header("User-Agent", PixivApi.userAgent)
			.header("App-OS", "android").header("App-OS-Version", android.os.Build.VERSION.RELEASE)
			.header("App-Version", PixivApi.APP_VERSION)
			.build()
		http.newCall(request).execute().use { r ->
			val body = r.body.string()
			if (!r.isSuccessful) throw PixivException(r.code, body.take(300))
			val t = json.decodeFromString<TokenResponse>(body)
			accessToken = t.accessToken
			refreshToken = t.refreshToken
			expiresAt = System.currentTimeMillis() + (t.expiresIn - 60) * 1000
			userName = t.user.name
			userId = t.user.id
			prefs().edit {
				putString("access", accessToken); putString("refresh", refreshToken)
				putLong("expires_at", expiresAt); putString("user_name", userName); putLong("user_id", userId)
			}
		}
	}

	/** A token that is valid right now, refreshing it when it is about to expire. */
	suspend fun validToken(): String {
		refreshLock.withLock {
			if (accessToken == null || System.currentTimeMillis() >= expiresAt) refresh()
			return accessToken!!
		}
	}

	/** Called by the API layer after a 400/401: refresh once and hand back the new token. */
	suspend fun forceRefresh(): String {
		refreshLock.withLock {
			refresh()
			return accessToken!!
		}
	}

	fun logout() {
		accessToken = null; refreshToken = null; expiresAt = 0; userName = null; userId = 0
		prefs().edit { clear() }
	}

	@Serializable
	private data class TokenResponse(
		@SerialName("access_token") val accessToken: String,
		@SerialName("refresh_token") val refreshToken: String,
		@SerialName("expires_in") val expiresIn: Long = 3600,
		val user: PixivUser = PixivUser(),
	)
}

class PixivException(val code: Int, message: String) : Exception("HTTP $code: $message")
