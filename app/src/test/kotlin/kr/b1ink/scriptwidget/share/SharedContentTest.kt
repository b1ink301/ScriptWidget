package kr.b1ink.scriptwidget.share

import kr.b1ink.scriptwidget.domain.model.ShareInput
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedContentTest {
    @Test
    fun `a lone link is a url`() {
        assertEquals(ShareInput(urls = listOf("https://example.com/a?b=1")), SharedContent.classify(listOf(" https://example.com/a?b=1 \n"), emptyList()))
    }

    @Test
    fun `text keeps its links as urls too`() {
        val text = "이 기사 봐 https://news.example/1 그리고 http://b.example"
        assertEquals(
            ShareInput(plainTexts = listOf(text), urls = listOf("https://news.example/1", "http://b.example")),
            SharedContent.classify(listOf(text), emptyList()),
        )
    }

    @Test
    fun `files are split into images and other files`() {
        val result = SharedContent.classify(
            emptyList(),
            listOf(
                SharedContent.File("/s/tmp/share/1/a.jpg", "image/jpeg"),
                SharedContent.File("/s/tmp/share/1/b.pdf", "application/pdf"),
                SharedContent.File("/s/tmp/share/1/c.png", null),
            ),
        )
        assertEquals(ShareInput(fileURLs = listOf("/s/tmp/share/1/b.pdf"), images = listOf("/s/tmp/share/1/a.jpg", "/s/tmp/share/1/c.png")), result)
    }

    @Test
    fun `blank text is ignored`() {
        assertEquals(ShareInput(), SharedContent.classify(listOf("  ", ""), emptyList()))
    }
}
