package com.mokona.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
	private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

	private val page = """
		{"illusts":[{"id":1,"title":"Uno","type":"illust","image_urls":{"square_medium":"sq","medium":"m","large":"l","original":null},
		  "user":{"id":7,"name":"Ana","account":"ana"},"tags":[{"name":"猫","translated_name":"cat"}],"page_count":1,"width":1000,"height":4000,
		  "x_restrict":0,"meta_single_page":{"original_image_url":"o1"},"meta_pages":[],"total_view":10,"total_bookmarks":2,"is_bookmarked":false,"unknown_field":1},
		 {"id":2,"title":"Dos","type":"manga","image_urls":{"large":"l2"},"user":{"id":7},"page_count":2,"width":300,"height":100,"x_restrict":1,
		  "meta_pages":[{"image_urls":{"large":"p0","original":"o0"}},{"image_urls":{"large":"p1","original":"o1"}}]}],
		 "next_url":"https://app-api.pixiv.net/v1/next"}
	""".trimIndent()

	@Test
	fun `a page decodes with missing and unknown fields`() {
		val p = json.decodeFromString<IllustsPage>(page)
		assertEquals(2, p.illusts.size)
		assertEquals("https://app-api.pixiv.net/v1/next", p.nextUrl)
		val one = p.illusts[0]
		assertEquals("Ana", one.user.name)
		assertEquals("cat", one.tags.single().translatedName)
		assertFalse(one.isAdult)
		assertFalse(one.isManga)
	}

	@Test
	fun `single and multi page works expose their files in order`() {
		val p = json.decodeFromString<IllustsPage>(page)
		assertEquals(listOf("o1"), p.illusts[0].originalUrls)
		assertEquals(listOf("l"), p.illusts[0].largeUrls)
		assertEquals(listOf("o0", "o1"), p.illusts[1].originalUrls)
		assertEquals(listOf("p0", "p1"), p.illusts[1].largeUrls)
		assertTrue(p.illusts[1].isManga)
		assertTrue(p.illusts[1].isAdult)
		assertEquals("https://www.pixiv.net/artworks/2", p.illusts[1].webUrl)
	}

	@Test
	fun `aspect ratio keeps the real proportion within sane bounds`() {
		val tall = Illust(width = 1000, height = 4000)
		val wide = Illust(width = 4000, height = 1000)
		val square = Illust(width = 500, height = 500)
		assertEquals(0.4f, tall.aspectRatio, 0.001f)
		assertEquals(2.5f, wide.aspectRatio, 0.001f)
		assertEquals(1f, square.aspectRatio, 0.001f)
		assertEquals(1f, Illust(width = 1, height = 0).aspectRatio, 0.001f)
	}
}
