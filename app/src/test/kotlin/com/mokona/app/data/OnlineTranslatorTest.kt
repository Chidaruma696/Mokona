package com.mokona.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineTranslatorTest {
	@Test
	fun `the answer of the endpoint is the translated segments joined`() {
		val body = """[[["Hola, ","Hello, ",null,null,10],["mundo.","world.",null,null,10]],null,"en",null,null,null,null,[]]"""
		assertEquals("Hola, mundo.", OnlineTranslator.parse(body))
	}

	@Test
	fun `garbage is an error, not an empty translation`() {
		assertThrows(Exception::class.java) { OnlineTranslator.parse("<html>blocked</html>") }
		assertThrows(Exception::class.java) { OnlineTranslator.parse("[]") }
	}
}
