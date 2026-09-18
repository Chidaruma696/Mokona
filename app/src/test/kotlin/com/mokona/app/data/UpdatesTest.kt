package com.mokona.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
	@Test
	fun `versions compare part by part`() {
		assertTrue(Updates.isNewer("0.2.0", "0.1.0"))
		assertTrue(Updates.isNewer("0.1.10", "0.1.9"))
		assertTrue(Updates.isNewer("1.0.0", "0.9.9"))
		assertTrue(Updates.isNewer("0.2.1", "0.2"))
		assertFalse(Updates.isNewer("0.2.0", "0.2.0"))
		assertFalse(Updates.isNewer("0.1.9", "0.2.0"))
		assertFalse("a suffix never wins", Updates.isNewer("0.2.0-beta", "0.2.0"))
	}

	@Test
	fun `a release picks the apk of the phone or any apk`() {
		val json = Json { ignoreUnknownKeys = true }
		val release = json.decodeFromString<Updates.Release>(
			"""{"tag_name":"v0.2.1","html_url":"https://github.com/x/releases/tag/v0.2.1","body":"notas","assets":[
			 {"name":"Mokona-0.2.1-armeabi-v7a.apk","browser_download_url":"https://dl/v7a"},
			 {"name":"Mokona-0.2.1-arm64-v8a.apk","browser_download_url":"https://dl/arm64"},
			 {"name":"notas.txt","browser_download_url":"https://dl/txt"}]}""",
		)
		assertEquals("0.2.1", release.version)
		assertEquals("https://dl/arm64", release.apkUrlFor(listOf("arm64-v8a", "armeabi-v7a")))
		assertEquals("https://dl/v7a", release.apkUrlFor(listOf("armeabi-v7a")))
		assertEquals("the first APK is the fallback for an unknown phone", "https://dl/v7a", release.apkUrlFor(listOf("x86_64")))
		assertEquals("https://dl/v7a", release.apkUrlFor(emptyList()))
		val none = json.decodeFromString<Updates.Release>("""{"tag_name":"v9","assets":[]}""")
		assertEquals(null, none.apkUrlFor(listOf("arm64-v8a")))
	}
}
