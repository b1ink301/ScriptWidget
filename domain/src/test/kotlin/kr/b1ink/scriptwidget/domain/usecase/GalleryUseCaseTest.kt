package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeCatalogSource
import kr.b1ink.scriptwidget.domain.fake.FakeCatalogUrls
import kr.b1ink.scriptwidget.domain.fake.FakeInstalledScripts
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.model.AddUrlResult
import kr.b1ink.scriptwidget.domain.model.CatalogEntry
import kr.b1ink.scriptwidget.domain.model.EntryStatus
import kr.b1ink.scriptwidget.domain.model.UpdateResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GalleryUseCaseTest {
    private val scripts = FakeScriptRepository()
    private val installed = FakeInstalledScripts()
    private val urls = FakeCatalogUrls()

    private fun entry(id: String, name: String = id, version: Int = 1) =
        CatalogEntry(id = id, name = name, title = name, category = "일정", script = "$id.js", version = version)

    private val builtin = FakeCatalogSource(
        id = "builtin", title = "기본 갤러리",
        entries = listOf(entry("events", "오늘 일정"), entry("todo", "할 일")),
        code = mapOf("events" to "// events v1", "todo" to "// todo v1"),
    )
    private val remote = FakeCatalogSource(
        id = "https://example.com/index.json", title = "Example",
        entries = listOf(entry("clock", "세계 시계")),
        code = mapOf("clock" to "// clock"),
    )
    private val remotes = mutableMapOf<String, FakeCatalogSource>("https://example.com/index.json" to remote)

    private val browse = BrowseGalleryUseCase(builtin, urls, { url -> remotes[url] ?: FakeCatalogSource(url, url, failure = "404") }, installed, scripts)
    private val install = InstallCatalogEntryUseCase(scripts, installed)
    private val update = UpdateCatalogEntryUseCase(scripts, installed)

    // --- browse ---

    @Test
    fun `the built-in catalog comes first, then each added catalog URL`() = runTest {
        urls.add("https://example.com/index.json")

        val sections = browse()

        assertEquals(listOf("기본 갤러리", "Example"), sections.map { it.title })
        assertEquals(listOf("오늘 일정", "할 일"), sections[0].items.map { it.entry.title })
        assertTrue(sections.all { it.error == null })
    }

    @Test
    fun `a catalog that fails to load is shown with its error, the others still load`() = runTest {
        urls.add("https://broken.example/index.json")

        val sections = browse()

        assertEquals(2, sections.size)
        assertEquals("404", sections[1].error)
        assertTrue(sections[1].items.isEmpty())
    }

    @Test
    fun `installed entries show their script, newer catalog versions show an update`() = runTest {
        install(builtin, builtin.entries[0])
        install(builtin, builtin.entries[1])
        builtin.entries = listOf(entry("events", "오늘 일정", version = 2), entry("todo", "할 일"))

        val items = browse()[0].items

        assertEquals(EntryStatus.UpdateAvailable("오늘 일정"), items[0].status)
        assertEquals(EntryStatus.Installed("할 일"), items[1].status)
    }

    @Test
    fun `an installed script the user deleted counts as not installed`() = runTest {
        install(builtin, builtin.entries[0])
        scripts.delete("오늘 일정")

        assertEquals(EntryStatus.NotInstalled, browse()[0].items[0].status)
    }

    @Test
    fun `a script identical to a catalog entry counts as installed from it`() = runTest {
        scripts.files["오늘 일정"] = "// events v1" // seeded before the gallery existed

        assertEquals(EntryStatus.Installed("오늘 일정"), browse()[0].items[0].status)
    }

    @Test
    fun `a same-named script with other code is not taken for the catalog entry`() = runTest {
        scripts.files["오늘 일정"] = "// something else"

        assertEquals(EntryStatus.NotInstalled, browse()[0].items[0].status)
    }

    // --- first launch ---

    @Test
    fun `first launch installs every catalog entry and remembers them`() = runTest {
        SeedFromCatalogUseCase(scripts, builtin, install)()

        assertEquals(setOf("오늘 일정", "할 일"), scripts.files.keys)
        assertEquals(2, installed.records.size)
    }

    @Test
    fun `seeding does nothing once there are scripts`() = runTest {
        scripts.files["mine"] = "x"

        SeedFromCatalogUseCase(scripts, builtin, install)()

        assertEquals(setOf("mine"), scripts.files.keys)
    }

    // --- install ---

    @Test
    fun `installing copies the code under the entry name and remembers where it came from`() = runTest {
        val name = install(builtin, builtin.entries[0])

        assertEquals("오늘 일정", name)
        assertEquals("// events v1", scripts.files["오늘 일정"])
        assertEquals("events", installed.records.single().entryId)
    }

    @Test
    fun `an existing script with the same name is never overwritten`() = runTest {
        scripts.files["오늘 일정"] = "mine"

        val name = install(builtin, builtin.entries[0])

        assertEquals("오늘 일정 2", name)
        assertEquals("mine", scripts.files["오늘 일정"])
    }

    // --- update ---

    @Test
    fun `an unchanged installed script is updated in place`() = runTest {
        install(builtin, builtin.entries[0])
        builtin.entries = listOf(entry("events", "오늘 일정", version = 2))
        builtin.code = mapOf("events" to "// events v2")

        val result = update(builtin, builtin.entries[0])

        assertEquals(UpdateResult.Updated("오늘 일정"), result)
        assertEquals("// events v2", scripts.files["오늘 일정"])
        assertEquals(EntryStatus.Installed("오늘 일정"), browse()[0].items[0].status)
    }

    @Test
    fun `a script the user edited is kept and the update is installed next to it`() = runTest {
        install(builtin, builtin.entries[0])
        scripts.files["오늘 일정"] = "// my changes"
        builtin.entries = listOf(entry("events", "오늘 일정", version = 2))
        builtin.code = mapOf("events" to "// events v2")

        val result = update(builtin, builtin.entries[0])

        assertEquals(UpdateResult.InstalledAsCopy("오늘 일정 2"), result)
        assertEquals("// my changes", scripts.files["오늘 일정"])
        assertEquals("// events v2", scripts.files["오늘 일정 2"])
    }

    // --- catalog URLs ---

    @Test
    fun `catalog URLs must be https and are not added twice`() = runTest {
        val add = AddCatalogUrlUseCase(urls)

        assertEquals(AddUrlResult.Added, add(" https://example.com/index.json "))
        assertIs<AddUrlResult.Invalid>(add("http://example.com/index.json"))
        assertIs<AddUrlResult.Invalid>(add("not a url"))
        assertEquals(AddUrlResult.AlreadyAdded, add("https://example.com/index.json"))
        assertEquals(listOf("https://example.com/index.json"), urls.urls.first())
    }

    @Test
    fun `removing a catalog URL keeps the scripts installed from it`() = runTest {
        urls.add("https://example.com/index.json")
        install(remote, remote.entries[0])

        RemoveCatalogUrlUseCase(urls)("https://example.com/index.json")

        assertTrue(urls.urls.first().isEmpty())
        assertEquals("// clock", scripts.files["세계 시계"])
        assertNull(browse().getOrNull(1))
    }
}
