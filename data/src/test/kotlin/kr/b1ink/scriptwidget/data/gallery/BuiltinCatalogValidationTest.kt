package kr.b1ink.scriptwidget.data.gallery

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.model.CatalogProblem
import kr.b1ink.scriptwidget.domain.model.CatalogValidator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The gallery that ships with the app (and is read from GitHub) must pass the catalog checks. */
class BuiltinCatalogValidationTest {
    private val source = BuiltinCatalogSource()

    @Test
    fun `the built-in catalog has no errors or warnings`() = runTest {
        val catalog = source.catalog()
        val code = catalog.entries.associate { it.id to runCatching { source.code(it) }.getOrNull() }

        val problems = CatalogValidator.validate(catalog, code::get)

        assertEquals(emptyList<CatalogProblem>(), problems, problems.joinToString("\n") { "${it.entryId}: ${it.message}" })
    }

    @Test
    fun `scripts in other languages need the same permissions`() = runTest {
        val english = BuiltinCatalogSource(language = { "en" })
        val catalog = source.catalog()
        val code = catalog.entries.associate { it.id to runCatching { english.code(it) }.getOrNull() }

        val problems = CatalogValidator.validate(catalog, code::get)

        assertEquals(emptyList<CatalogProblem>(), problems, problems.joinToString("\n") { "${it.entryId}: ${it.message}" })
    }

    @Test
    fun `every sample script is listed in the catalog`() = runTest {
        val listed = source.catalog().entries.flatMap { it.allScripts() }.toSet()
        val root = File("src/main/resources/samples")
        val files = root.walk().filter { it.extension == "js" }.map { it.relativeTo(root).path }.toSet()

        assertEquals(emptySet(), files - listed, "samples missing from catalog.json")
    }

    @Test
    fun `every entry has an English title, description and category`() = runTest {
        val missing = source.catalog().entries.filter { entry ->
            val en = entry.translations["en"]
            en == null || en.title.isBlank() || en.description.isBlank() || en.category.isBlank()
        }

        assertEquals(emptyList(), missing.map { it.id }, "entries without a complete English translation")
    }
}
