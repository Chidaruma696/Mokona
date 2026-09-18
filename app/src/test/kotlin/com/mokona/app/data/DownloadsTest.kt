package com.mokona.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsTest {
	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun `a saved work points every page at its local file`() {
		val illust = Illust(id = 5, title = "T", pageCount = 2, imageUrls = ImageUrls(squareMedium = "sq", large = "https://i.pximg.net/l"),
			metaPages = listOf(MetaPage(ImageUrls(large = "https://i.pximg.net/p0")), MetaPage(ImageUrls(large = "https://i.pximg.net/p1"))))
		val work = DownloadedWork(illust, listOf("/data/works/5/p0.jpg", "/data/works/5/p1.png"), 1L)
		val offline = work.offline()
		assertEquals(listOf("file:///data/works/5/p0.jpg", "file:///data/works/5/p1.png"), offline.largeUrls)
		assertEquals(offline.largeUrls, offline.originalUrls)
		assertEquals("the grid thumbnail stays remote", "sq", offline.imageUrls.squareMedium)
		assertEquals(5L, offline.id)
	}

	@Test
	fun `a single page work has no meta pages and still opens`() {
		val work = DownloadedWork(Illust(id = 1, imageUrls = ImageUrls(large = "l")), listOf("/w/1/p0.jpg"), 1L)
		val offline = work.offline()
		assertEquals(listOf("file:///w/1/p0.jpg"), offline.largeUrls)
		assertEquals(listOf("file:///w/1/p0.jpg"), offline.originalUrls)
		assertEquals(0, offline.metaPages.size)
	}

	@Test
	fun `the index survives a round trip through json`() {
		val work = DownloadedWork(Illust(id = 9, title = "九", user = PixivUser(id = 3, name = "N")), listOf("/w/9/p0.jpg"), 42L)
		val text = json.encodeToString(DownloadedWork.serializer(), work)
		assertEquals(work, json.decodeFromString(DownloadedWork.serializer(), text))
		val list = WallpaperList(id = 1, name = "Fondos", items = listOf(WallpaperItem(9, "九", "N", "sq")))
		assertEquals(list, json.decodeFromString(WallpaperList.serializer(), json.encodeToString(WallpaperList.serializer(), list)))
	}
}
