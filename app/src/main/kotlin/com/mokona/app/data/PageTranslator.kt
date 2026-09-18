package com.mokona.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Manga in your language. ML Kit reads the text of a page on the phone (Japanese, Chinese, Korean
 * or English; the recognisers travel in the APK). Each bubble is then translated online with
 * Google Translate, no download needed, or, if the user turned offline mode on and fetched the
 * dictionaries from Settings, with ML Kit's models without any connection.
 */
object PageTranslator {
	enum class Source(val tag: String) {
		JAPANESE(TranslateLanguage.JAPANESE), CHINESE(TranslateLanguage.CHINESE), KOREAN(TranslateLanguage.KOREAN), ENGLISH(TranslateLanguage.ENGLISH);
	}

	/** One recognised block, in bitmap pixels. */
	data class Bubble(val box: Rect, val original: String, val translated: String)
	/** `source` is the language the page turned out to be in. */
	data class Result(val width: Int, val height: Int, val bubbles: List<Bubble>, val source: Source = Source.JAPANESE)

	sealed interface Status {
		data object Idle : Status
		data object Loading : Status
		data object DownloadingModel : Status
		data object Recognizing : Status
		data object Translating : Status
		data class Failed(val message: String) : Status
	}

	private val cache = LruCache<String, Result>(32)
	private val lock = Mutex()

	/** Language the bubbles are translated into: the app's language, or the phone's when following it. */
	fun target(): String {
		val tag = (if (AppLanguage.current == "system") Locale.getDefault().language else AppLanguage.current).lowercase()
		return TranslateLanguage.fromLanguageTag(tag) ?: TranslateLanguage.ENGLISH
	}

	fun cached(url: String): Result? = cache.get("${target()}:${AppLanguage.offline}:$url")

	/**
	 * Recognises and translates one page. The language is found by the page itself: the Japanese
	 * recogniser goes first, the script of what it read says whether the page is Japanese (kana),
	 * Chinese (ideographs and no kana), Korean (hangul, or nothing readable) or English (Latin
	 * letters), and the matching recogniser reads it again when it is not Japanese.
	 * Results are cached per page, target and mode.
	 */
	suspend fun translate(context: Context, url: String, onStatus: (Status) -> Unit = {}): Result {
		val key = "${target()}:${AppLanguage.offline}:$url"
		cache.get(key)?.let { return it }
		return lock.withLock {
			cache.get(key)?.let { return@withLock it }
			onStatus(Status.Loading)
			val bitmap = loadBitmap(context, url)
			onStatus(Status.Recognizing)
			val (source, blocks) = detectAndRecognise(bitmap)
			val result = if (blocks.isEmpty()) {
				Result(bitmap.width, bitmap.height, emptyList(), source)
			} else if (source.tag == target()) {
				// Reading English on an English app: nothing to translate; the boxes still help.
				Result(bitmap.width, bitmap.height, blocks.map { (box, text) -> Bubble(box, text, text) }, source)
			} else if (!AppLanguage.offline) {
				onStatus(Status.Translating)
				val translated = OnlineTranslator.translate(blocks.map { it.second }, source.tag, target())
				Result(bitmap.width, bitmap.height, blocks.mapIndexed { i, (box, text) -> Bubble(box, text, translated[i]) }, source)
			} else {
				if (!TranslationModels.ready(source)) {
					onStatus(Status.DownloadingModel)
					TranslationModels.download(source)
				}
				val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source.tag).setTargetLanguage(target()).build())
				try {
					onStatus(Status.Translating)
					val bubbles = blocks.map { (box, text) -> Bubble(box, text, translator.translate(text).await()) }
					Result(bitmap.width, bitmap.height, bubbles, source)
				} finally {
					translator.close()
				}
			}
			cache.put(key, result)
			onStatus(Status.Idle)
			result
		}
	}

	private suspend fun loadBitmap(context: Context, url: String): Bitmap {
		val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
		val result = SingletonImageLoader.get(context).execute(request)
		val image = (result as? SuccessResult)?.image ?: throw IllegalStateException("image")
		return image.toBitmap()
	}

	private fun count(text: String, pred: (Char) -> Boolean) = text.count(pred)
	private fun kana(c: Char) = c in '\u3040'..'\u30FF'
	private fun hangul(c: Char) = c in '\uAC00'..'\uD7AF' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'
	private fun han(c: Char) = c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF'
	private fun latin(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '\u00C0'..'\u024F'

	/** Which language the page is in, judged by the script of what the recognisers read. */
	private suspend fun detectAndRecognise(bitmap: Bitmap): Pair<Source, List<Pair<Rect, String>>> {
		val ja = recognise(bitmap, Source.JAPANESE)
		val text = ja.joinToString("") { it.second }
		val nKana = count(text, ::kana); val nHan = count(text, ::han); val nLatin = count(text, ::latin); val nHangul = count(text, ::hangul)
		// Kana settles it: Japanese.
		if (nKana > 0 && nKana >= nLatin / 4) return Source.JAPANESE to ja
		// Ideographs and no kana: Chinese, read again with its own recogniser.
		if (nHan > 0 && nHan >= nLatin) return Source.CHINESE to recognise(bitmap, Source.CHINESE).ifEmpty { ja }
		// Mostly Latin letters: English, with the Latin recogniser.
		if (nLatin > 0 && nLatin > nHangul) return Source.ENGLISH to recognise(bitmap, Source.ENGLISH).ifEmpty { ja }
		// Little or nothing readable for Japanese: try Korean, then English, else keep what there is.
		val ko = recognise(bitmap, Source.KOREAN)
		if (count(ko.joinToString("") { it.second }, ::hangul) > 0) return Source.KOREAN to ko
		val en = recognise(bitmap, Source.ENGLISH)
		if (count(en.joinToString("") { it.second }, ::latin) > 0) return Source.ENGLISH to en
		return Source.JAPANESE to ja
	}

	private suspend fun recognise(bitmap: Bitmap, source: Source): List<Pair<Rect, String>> = withContext(Dispatchers.Default) {
		val options = when (source) {
			Source.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
			Source.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
			Source.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
			Source.ENGLISH -> TextRecognizerOptions.DEFAULT_OPTIONS
		}
		val recognizer = TextRecognition.getClient(options)
		try {
			val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
			val blocks = text.textBlocks.mapNotNull { block ->
				val box = block.boundingBox ?: return@mapNotNull null
				// Japanese and Chinese have no spaces between lines of a bubble; Korean does.
				val joined = block.lines.joinToString(if (source == Source.KOREAN || source == Source.ENGLISH) " " else "") { it.text.trim() }
				if (joined.isBlank()) null else box to joined
			}
			// Reading order: bands from top to bottom; inside a band, right to left for manga, left to right for English.
			val band = (bitmap.height * 0.08f).coerceAtLeast(1f)
			blocks.sortedWith(compareBy<Pair<Rect, String>> { (it.first.centerY() / band).toInt() }.thenBy { if (source == Source.ENGLISH) it.first.centerX() else -it.first.centerX() })
		} finally {
			recognizer.close()
		}
	}
}

/** Mirror of the app language for code outside Compose. Kept in sync by AppPrefs. */
object AppLanguage {
	@Volatile var current: String = "en"
	/** Translate with the downloaded dictionaries instead of Google Translate online. */
	@Volatile var offline: Boolean = false
}
