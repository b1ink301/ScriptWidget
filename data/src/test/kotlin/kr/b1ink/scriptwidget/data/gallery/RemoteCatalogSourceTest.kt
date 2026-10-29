package kr.b1ink.scriptwidget.data.gallery

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.engine.FakeHttpGateway
import kr.b1ink.scriptwidget.data.engine.HttpResponseSpec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteCatalogSourceTest {
    private val index = """
        {"title":"Community","entries":[
          {"id":"clock","name":"세계 시계","category":"정보","description":"d","script":"scripts/clock.js","version":2,"sizes":["small"]}
        ]}
    """.trimIndent()

    private val http = FakeHttpGateway().apply {
        responses = { spec ->
            when (spec.url) {
                "https://example.com/gallery/index.json" -> HttpResponseSpec(spec.url, 200, emptyMap(), "application/json", index)
                "https://example.com/gallery/scripts/clock.js" -> HttpResponseSpec(spec.url, 200, emptyMap(), "text/javascript", "// clock")
                else -> HttpResponseSpec(spec.url, 404, emptyMap(), "text/plain", "missing")
            }
        }
    }
    private val source = RemoteCatalogSource("https://example.com/gallery/index.json", http)

    @Test
    fun `reads the index and resolves script paths against it`() = runTest {
        val catalog = source.catalog()

        assertEquals("Community", catalog.title)
        assertEquals(2, catalog.entries.single().version)
        assertEquals("// clock", source.code(catalog.entries.single()))
        assertEquals("https://example.com/gallery/index.json", source.id)
    }

    @Test
    fun `http errors and bad json are reported`() = runTest {
        val missing = RemoteCatalogSource("https://example.com/none.json", http)
        assertContains(assertFailsWith<Exception> { missing.catalog() }.message.orEmpty(), "404")

        http.responses = { HttpResponseSpec(it.url, 200, emptyMap(), "text/plain", "not json") }
        assertFailsWith<Exception> { source.catalog() }
    }

    @Test
    fun `scripts outside https are refused`() = runTest {
        val entry = source.catalog().entries.single().copy(script = "http://evil.example/x.js")

        assertFailsWith<Exception> { source.code(entry) }
    }
}
