package com.mokona.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * The default translator: Google Translate's public web endpoint, no key, no download. Each
 * bubble is one request; a few run at a time. When there is no connection this fails, and the
 * offline dictionaries (Settings › Reader) are the way around it.
 */
object OnlineTranslator {
	private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
	private val json = Json { ignoreUnknownKeys = true; isLenient = true }
	private val lanes = Semaphore(4)

	/** ML Kit's tags are what the app speaks; Google's endpoint wants zh-CN for Chinese. */
	private fun code(tag: String) = if (tag == "zh") "zh-CN" else tag

	suspend fun translate(texts: List<String>, from: String, to: String): List<String> = coroutineScope {
		texts.map { text -> async { lanes.withPermit { one(text, from, to) } } }.map { it.await() }
	}

	private suspend fun one(text: String, from: String, to: String): String = withContext(Dispatchers.IO) {
		val url = ENDPOINT.toHttpUrl().newBuilder()
			.addQueryParameter("client", "gtx").addQueryParameter("sl", code(from)).addQueryParameter("tl", code(to))
			.addQueryParameter("dt", "t").addQueryParameter("q", text).build()
		val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mokona").build()
		PixivApi.client.newCall(request).execute().use { r ->
			if (!r.isSuccessful) throw IllegalStateException("Google Translate ${r.code}")
			parse(r.body.string())
		}
	}

	/** The endpoint answers `[[["hola", "hello", …], ["mundo", "world", …]], null, "en", …]`: the translation is the first item of every segment, joined. */
	fun parse(body: String): String {
		val root = json.parseToJsonElement(body).jsonArray
		return root[0].jsonArray.joinToString("") { seg -> seg.jsonArray[0].jsonPrimitive.content }
	}
}
