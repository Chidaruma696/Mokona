package com.mokona.app.data

import com.mokona.app.data.PageTranslator.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageTextTest {
	@Test
	fun `the script of what was read says the language`() {
		assertEquals(Source.JAPANESE, PageText.guess("そんなことないよ！"))
		assertEquals(Source.JAPANESE, PageText.guess("東京へ行く"))
		assertEquals(Source.CHINESE, PageText.guess("我们走吧"))
		assertEquals(Source.ENGLISH, PageText.guess("Let's go, now!"))
		assertEquals("a stray ideograph in Latin text stays English", Source.ENGLISH, PageText.guess("Hello 世"))
		assertNull(PageText.guess(""))
		assertNull(PageText.guess("!!! ... 123"))
		assertNull("hangul is for the Korean recogniser to confirm", PageText.guess("안녕하세요"))
	}

	@Test
	fun `lines join without spaces in japanese and chinese, with spaces otherwise`() {
		assertEquals("そんな事ない", PageText.joinLines(listOf(" そんな", "事ない "), Source.JAPANESE))
		assertEquals("我们走", PageText.joinLines(listOf("我们", "走"), Source.CHINESE))
		assertEquals("let us go", PageText.joinLines(listOf("let us", "go"), Source.ENGLISH))
		assertEquals("안녕 하세요", PageText.joinLines(listOf("안녕", "하세요"), Source.KOREAN))
	}

	data class B(val name: String, val cx: Int, val cy: Int)

	@Test
	fun `manga reads top to bottom and right to left, english left to right`() {
		val boxes = listOf(B("bottom-left", 100, 900), B("top-left", 100, 100), B("top-right", 900, 120), B("mid", 500, 500))
		val manga = PageText.readingOrder(boxes, 1000, Source.JAPANESE, { it.cx }, { it.cy }).map { it.name }
		assertEquals(listOf("top-right", "top-left", "mid", "bottom-left"), manga)
		val english = PageText.readingOrder(boxes, 1000, Source.ENGLISH, { it.cx }, { it.cy }).map { it.name }
		assertEquals(listOf("top-left", "top-right", "mid", "bottom-left"), english)
	}
}
