package kr.b1ink.scriptwidget.data.gallery

import kr.b1ink.scriptwidget.domain.model.Catalog
import kr.b1ink.scriptwidget.domain.model.CatalogProblem
import kr.b1ink.scriptwidget.domain.model.CatalogValidator
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks a community catalog before it is shared (skipped unless a file is given):
 * `./gradlew :data:test --tests '*CatalogFileCheck*' -Pcatalog=/path/to/index.json`
 * Scripts are read next to the catalog, as the app does.
 */
class CatalogFileCheck {
    @Test
    fun `the given catalog has no errors`() {
        val path = System.getProperty("catalog.file").orEmpty()
        assumeTrue("set -Pcatalog=/path/to/index.json", path.isNotBlank())
        val file = File(path)
        val catalog = Json { ignoreUnknownKeys = true }.decodeFromString(Catalog.serializer(), file.readText())
        val scripts = catalog.entries.associate { it.id to file.resolveSibling(it.script).takeIf(File::isFile)?.readText() }

        val problems = CatalogValidator.validate(catalog, scripts::get)

        problems.forEach { println("${if (it is CatalogProblem.Error) "ERROR" else "warning"} ${it.entryId}: ${it.message}") }
        println("${catalog.entries.size} entries, ${problems.count { it is CatalogProblem.Error }} errors")
        assertTrue(problems.none { it is CatalogProblem.Error }, problems.joinToString("\n") { "${it.entryId}: ${it.message}" })
    }
}
