package kr.b1ink.scriptwidget.data.gallery

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeCatalogSource
import kr.b1ink.scriptwidget.domain.model.CatalogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The official gallery: the catalog in the GitHub repository, with the bundled copy as fallback. */
class OfficialCatalogSourceTest {
    private fun entry(id: String, version: Int = 1) = CatalogEntry(id = id, name = id, script = "$id.js", version = version)

    private val bundled = FakeCatalogSource("builtin", "기본 갤러리", listOf(entry("events")), mapOf("events" to "// bundled events"))
    private val online = FakeCatalogSource(
        "https://raw.example/catalog.json", "기본 갤러리",
        listOf(entry("events", version = 2), entry("new_sample")),
        mapOf("events" to "// events v2", "new_sample" to "// new"),
    )
    private val source = OfficialCatalogSource(online, bundled)

    @Test
    fun `it keeps the built-in id so installs and updates stay connected`() {
        assertEquals("builtin", source.id)
    }

    @Test
    fun `the online catalog brings new samples and newer versions without an app update`() = runTest {
        val entries = source.catalog().entries

        assertEquals(listOf("events" to 2, "new_sample" to 1), entries.map { it.id to it.version })
        assertEquals("// events v2", source.code(entries[0]))
    }

    @Test
    fun `offline, the bundled catalog is used`() = runTest {
        online.failure = "offline"
        online.code = emptyMap()

        val entries = source.catalog().entries

        assertEquals(listOf("events" to 1), entries.map { it.id to it.version })
        assertEquals("// bundled events", source.code(entries.single()))
    }

    @Test
    fun `if downloading a script fails, the bundled copy is installed when there is one`() = runTest {
        online.code = emptyMap()

        assertEquals("// bundled events", source.code(entry("events", version = 2)))
        assertFailsWith<Exception> { source.code(entry("new_sample")) }
    }
}
