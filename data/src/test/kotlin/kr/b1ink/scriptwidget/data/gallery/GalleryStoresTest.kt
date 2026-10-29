package kr.b1ink.scriptwidget.data.gallery

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.model.InstalledScript
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryStoresTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `installed scripts are kept across instances, one record per script`() = runTest {
        val file = tmp.root.resolve("gallery/installed.json")
        FileInstalledScriptRepository(file).save(InstalledScript("오늘 일정", "builtin", "today_events", 1, "h1"))
        FileInstalledScriptRepository(file).save(InstalledScript("오늘 일정", "builtin", "today_events", 2, "h2"))

        assertEquals(listOf(2), FileInstalledScriptRepository(file).all().map { it.version })
    }

    @Test
    fun `catalog urls are kept in order`() = runTest {
        val file = tmp.root.resolve("gallery/urls.json")
        FileCatalogUrlRepository(file).add("https://a.example/index.json")
        FileCatalogUrlRepository(file).add("https://b.example/index.json")
        FileCatalogUrlRepository(file).remove("https://a.example/index.json")

        assertEquals(listOf("https://b.example/index.json"), FileCatalogUrlRepository(file).urls.first())
    }
}
