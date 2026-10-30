package kr.b1ink.scriptwidget.data.gallery

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.design.GeneratorTexts
import kr.b1ink.scriptwidget.data.design.JsDesignScriptGenerator
import kr.b1ink.scriptwidget.domain.model.DesignHeader
import kr.b1ink.scriptwidget.domain.model.DesignRead
import kr.b1ink.scriptwidget.domain.model.DesignTemplates
import kr.b1ink.scriptwidget.domain.model.TemplateTexts
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The gallery's "Designs" are the designer templates, generated: a template changed without
 * regenerating them fails here. Regenerate with
 * `./gradlew :data:test --tests '*DesignSamplesTest*' -PwriteDesigns`.
 */
class DesignSamplesTest {
    private val dir = File("src/main/resources/samples")

    private fun expected(): Map<String, String> = GALLERY_TEMPLATES.flatMap { (template, id) ->
        listOf(
            "designs/$id.js" to sample(template, TemplateTexts.KOREAN, GeneratorTexts.KOREAN),
            "designs/$id.en.js" to sample(template, TemplateTexts.ENGLISH, GeneratorTexts.ENGLISH),
        )
    }.toMap()

    private fun sample(template: DesignTemplates, texts: TemplateTexts, words: GeneratorTexts): String {
        val design = template.design(texts)
        return DesignHeader.attach(design, JsDesignScriptGenerator(words).generate(design))
    }

    @Test
    fun `the design samples are the designer templates`() {
        val expected = expected()
        if (System.getProperty("design.write") == "true") {
            expected.forEach { (path, code) -> File(dir, path).apply { parentFile.mkdirs() }.writeText(code) }
        }

        val stale = expected.filter { (path, code) -> File(dir, path).takeIf { it.exists() }?.readText() != code }.keys
        assertEquals(emptySet(), stale, "regenerate: ./gradlew :data:test --tests '*DesignSamplesTest*' -PwriteDesigns")
        val extra = File(dir, "designs").listFiles().orEmpty().map { "designs/${it.name}" }.toSet() - expected.keys
        assertEquals(emptySet(), extra, "files in samples/designs that no template makes")
    }

    @Test
    fun `every design sample is in the catalog, opens in the designer and comes in English`() = runTest {
        val source = BuiltinCatalogSource()
        val entries = source.catalog().entries.associateBy { it.script }

        GALLERY_TEMPLATES.forEach { (_, id) ->
            val entry = entries["designs/$id.js"] ?: error("designs/$id.js is not in catalog.json")
            assertEquals("designs/$id.en.js", entry.localizedScripts["en"], id)
            assertEquals(false, entry.seed, "$id: design samples are not installed on first launch")
            assertIs<DesignRead.Designed>(DesignHeader.read(BuiltinCatalogSource(language = { "en" }).code(entry)), id)
            assertIs<DesignRead.Designed>(DesignHeader.read(source.code(entry)), id)
        }
    }

    companion object {
        /** Templates offered in the gallery, with their file names. */
        val GALLERY_TEMPLATES = listOf(
            DesignTemplates.Clock to "clock",
            DesignTemplates.Battery to "battery",
            DesignTemplates.NextEvent to "next_event",
            DesignTemplates.Reminders to "reminders",
            DesignTemplates.Weather to "weather",
            DesignTemplates.GitHubStars to "github_stars",
            DesignTemplates.PhotoFrame to "photo_frame",
            DesignTemplates.DDay to "dday",
        )
    }
}
