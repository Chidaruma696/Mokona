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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Manga in your language, without leaving the phone: ML Kit reads the text of a page (Japanese,
 * Chinese or Korean, models bundled in the APK) and translates each speech bubble with an offline
 * model that downloads once (~30 MB per language). Nothing about the page leaves the device.
 */
object PageTranslator {
	enum class Source(val tag: String) {
		JAPANESE(TranslateLanguage.JAPANESE), CHINESE(TranslateLanguage.CHINESE), KOREAN(TranslateLanguage.KOREAN);
	}

	/** One recognised block, in bitmap pixels. */
	data class Bubble(val box: Rect, val original: String, val translated: String)
	data class Result(val width: Int, val height: Int, val bubbles: List<Bubble>)

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

	fun cached(url: String, source: Source): Result? = cache.get("$source:${target()}:$url")

	/** Recognises and translates one page; results are cached per page, language and target. */
	suspend fun translate(context: Context, url: String, source: Source, onStatus: (Status) -> Unit = {}): Result {
		val key = "$source:${target()}:$url"
		cache.get(key)?.let { return it }
		return lock.withLock {
			cache.get(key)?.let { return@withLock it }
			onStatus(Status.Loading)
			val bitmap = loadBitmap(context, url)
			onStatus(Status.Recognizing)
			val blocks = recognise(bitmap, source)
			val result = if (blocks.isEmpty()) {
				Result(bitmap.width, bitmap.height, emptyList())
			} else {
				val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source.tag).setTargetLanguage(target()).build())
				try {
					onStatus(Status.DownloadingModel)
					translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
					onStatus(Status.Translating)
					val bubbles = blocks.map { (box, text) -> Bubble(box, text, translator.translate(text).await()) }
					Result(bitmap.width, bitmap.height, bubbles)
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

	private suspend fun recognise(bitmap: Bitmap, source: Source): List<Pair<Rect, String>> = withContext(Dispatchers.Default) {
		val options = when (source) {
			Source.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
			Source.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
			Source.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
		}
		val recognizer = TextRecognition.getClient(options)
		try {
			val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
			text.textBlocks.mapNotNull { block ->
				val box = block.boundingBox ?: return@mapNotNull null
				// Japanese and Chinese have no spaces between lines of a bubble; Korean does.
				val joined = block.lines.joinToString(if (source == Source.KOREAN) " " else "") { it.text.trim() }
				if (joined.isBlank()) null else box to joined
			}
		} finally {
			recognizer.close()
		}
	}
}

/** Mirror of the app language for code outside Compose. Kept in sync by AppPrefs. */
object AppLanguage {
	@Volatile var current: String = "en"
}
