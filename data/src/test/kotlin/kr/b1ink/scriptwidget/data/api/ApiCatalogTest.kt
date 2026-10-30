package kr.b1ink.scriptwidget.data.api

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.engine.Prelude
import kr.b1ink.scriptwidget.data.engine.QuickJsScriptEngine
import kr.b1ink.scriptwidget.data.engine.fakeServices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The API reference is read from the prelude itself, so it can't drift from what scripts can call. */
class ApiCatalogTest {
    private suspend fun catalog() = ApiCatalog.load(QuickJsScriptEngine(Prelude.load(), fakeServices(files = kr.b1ink.scriptwidget.data.files.SandboxedFileSystem(kotlin.io.path.createTempDirectory().toFile())), timeoutMillis = 5_000))

    @Test
    fun `lists classes with constructors, methods and properties`() = runTest {
        val entries = catalog()
        val widget = entries.single { it.name == "ListWidget" }

        assertEquals(ApiEntry.Kind.Class, widget.kind)
        assertTrue(widget.member("addText")!!.let { it.kind == ApiMember.Kind.Method && it.params == listOf("text") && !it.isStatic })
        assertTrue(widget.member("backgroundColor")!!.kind == ApiMember.Kind.Property)
        assertTrue(widget.summary.isNotBlank())
        assertEquals("https://docs.scriptable.app/listwidget/", widget.docsUrl)

        val font = entries.single { it.name == "Font" }
        assertEquals(listOf("name", "size"), font.constructorParams)
        assertTrue(font.member("boldSystemFont")!!.isStatic)
    }

    @Test
    fun `covers the elements returned by widgets and plain objects`() = runTest {
        val names = catalog().map { it.name }.toSet()

        assertTrue(names.containsAll(listOf("WidgetText", "WidgetStack", "WidgetImage", "Request", "FileManager", "Alert", "Calendar", "Photos")))
        assertTrue(names.containsAll(listOf("config", "args", "Script", "Pasteboard", "importModule")))
    }

    @Test
    fun `private helpers are hidden`() = runTest {
        val all = catalog().flatMap { e -> e.members.map { "${e.name}.${it.name}" } }
        assertTrue(all.none { it.substringAfter('.').startsWith("_") }, all.filter { it.substringAfter('.').startsWith("_") }.toString())
    }

    @Test
    fun `async methods are marked`() = runTest {
        val request = catalog().single { it.name == "Request" }
        assertNotNull(request.member("loadJSON")).also { assertTrue(it.isAsync) }
        assertTrue(request.member("url")!!.kind == ApiMember.Kind.Property)
    }

    @Test
    fun `every entry has an English description and Korean stays the default`() = runTest {
        val korean = catalog()
        val english = ApiCatalog.load(QuickJsScriptEngine(Prelude.load(), fakeServices(files = kr.b1ink.scriptwidget.data.files.SandboxedFileSystem(kotlin.io.path.createTempDirectory().toFile())), timeoutMillis = 5_000), language = "en")

        assertEquals(ApiCatalog.NAMES.toSet(), english.map { it.name }.toSet())
        for (name in ApiCatalog.NAMES) {
            val ko = ApiDocs.summary(name)
            val en = ApiDocs.summary(name, "en")
            assertTrue(ko.isNotBlank(), "$name has no Korean description")
            assertTrue(en.isNotBlank(), "$name has no English description")
            assertTrue(en != ko, "$name: English description equals the Korean one")
            assertEquals(ko, korean.single { it.name == name }.summary)
            assertEquals(en, english.single { it.name == name }.summary)
        }
    }

    @Test
    fun `a loaded catalog can be shown in another language`() = runTest {
        val english = ApiCatalog.inLanguage(catalog(), "en")

        assertEquals(ApiDocs.summary("ListWidget", "en"), english.single { it.name == "ListWidget" }.summary)
        assertEquals(ApiDocs.summary("ListWidget"), ApiCatalog.inLanguage(english, "ko").single { it.name == "ListWidget" }.summary)
    }
}
