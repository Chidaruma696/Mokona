package com.mokona.app.data

/**
 * The pure rules of the page translator, kept apart from ML Kit and Android so they can be
 * tested on the JVM: which language a page is in judging by the script of what was read, and
 * the order in which its lines are read.
 */
object PageText {
	fun kana(c: Char) = c in '\u3040'..'\u30FF'
	fun hangul(c: Char) = c in '\uAC00'..'\uD7AF' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'
	fun han(c: Char) = c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF'
	fun latin(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '\u00C0'..'\u024F'

	/**
	 * What the Japanese recogniser's output says about the page: Japanese when there is kana,
	 * Chinese when there are ideographs and no kana, English when Latin letters dominate, and
	 * null when nothing readable came out (the caller then tries the Korean and Latin recognisers).
	 */
	fun guess(japaneseText: String): PageTranslator.Source? {
		val nKana = japaneseText.count(::kana)
		val nHan = japaneseText.count(::han)
		val nLatin = japaneseText.count(::latin)
		val nHangul = japaneseText.count(::hangul)
		return when {
			nKana > 0 && nKana >= nLatin / 4 -> PageTranslator.Source.JAPANESE
			nHan > 0 && nHan >= nLatin -> PageTranslator.Source.CHINESE
			nLatin > 0 && nLatin > nHangul -> PageTranslator.Source.ENGLISH
			else -> null
		}
	}

	/** Lines of a bubble joined the way the language writes them: no spaces in Japanese and Chinese. */
	fun joinLines(lines: List<String>, source: PageTranslator.Source): String =
		lines.joinToString(if (source == PageTranslator.Source.KOREAN || source == PageTranslator.Source.ENGLISH) " " else "") { it.trim() }

	/**
	 * Reading order: bands from top to bottom (8 % of the page each); inside a band, right to
	 * left for manga, left to right for English.
	 */
	fun <T> readingOrder(items: List<T>, pageHeight: Int, source: PageTranslator.Source, centerX: (T) -> Int, centerY: (T) -> Int): List<T> {
		val band = (pageHeight * 0.08f).coerceAtLeast(1f)
		return items.sortedWith(compareBy<T> { (centerY(it) / band).toInt() }.thenBy { if (source == PageTranslator.Source.ENGLISH) centerX(it) else -centerX(it) })
	}
}
