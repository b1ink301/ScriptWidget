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
    fun `every sample script is listed in the catalog`() = runTest {
        val listed = source.catalog().entries.map { it.script }.toSet()
        val files = File("src/main/resources/samples").listFiles { f -> f.extension == "js" }.orEmpty().map { it.name }.toSet()

        assertEquals(emptySet(), files - listed, "samples missing from catalog.json")
    }
}
