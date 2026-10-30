package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeScriptDocuments
import kr.b1ink.scriptwidget.domain.model.FontResult
import kr.b1ink.scriptwidget.domain.repository.FontRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FontUseCaseTest {
    private class FakeFonts : FontRepository {
        val files = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

        /** Names without the extension, as the store lists them. */
        override val fonts = MutableStateFlow(emptyList<String>())

        override suspend fun save(fileName: String, bytes: ByteArray) {
            files.value = files.value + (fileName to bytes)
            fonts.value = files.value.keys.map { it.substringBeforeLast('.') }.sorted()
        }
    }

    private val fonts = FakeFonts()
    private val docs = FakeScriptDocuments().apply {
        bytes["content://a"] = byteArrayOf(0, 1, 0, 0, 9, 9)
        bytes["content://b"] = "OTTO....".toByteArray()
        bytes["content://c"] = "hello".toByteArray()
    }
    private val add = AddFontUseCase(fonts, docs)

    @Test
    fun `a TrueType or OpenType file is kept under its own name`() = runTest {
        assertEquals(FontResult.Saved("Pixel Sans"), add("content://a", "Pixel Sans.TTF"))
        assertEquals(FontResult.Saved("Serif"), add("content://b", "Serif"))

        assertEquals(setOf("Pixel Sans.ttf", "Serif.otf"), fonts.files.value.keys)
        assertEquals(listOf("Pixel Sans", "Serif"), fonts.fonts.first())
    }

    @Test
    fun `only the file name is used, never a path`() = runTest {
        assertEquals(FontResult.Saved("evil"), add("content://a", "../../evil.ttf"))
        assertEquals(setOf("evil.ttf"), fonts.files.value.keys)
    }

    @Test
    fun `other files are refused`() = runTest {
        assertEquals(FontResult.NotAFont, add("content://c", "notes.ttf"))
        assertIs<FontResult.Failed>(add("content://missing", "x.ttf"))
        assertEquals(emptyMap(), fonts.files.value)
    }
}
