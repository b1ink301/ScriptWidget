package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.model.DesignHeader
import kr.b1ink.scriptwidget.domain.model.DesignRead
import kr.b1ink.scriptwidget.domain.model.DesignText
import kr.b1ink.scriptwidget.domain.model.DesignTemplates
import kr.b1ink.scriptwidget.domain.model.ScriptAppearance
import kr.b1ink.scriptwidget.domain.model.ScriptCommandResult
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kr.b1ink.scriptwidget.domain.model.WidgetDesign
import kr.b1ink.scriptwidget.domain.repository.DesignScriptGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignUseCasesTest {
    private val scripts = FakeScriptRepository(mapOf("design_1" to "old"))
    private val bindings = FakeWidgetBindingRepository(WidgetBinding(7, "clock", null))
    private val scheduler = FakeRefreshScheduler()
    private val generator = DesignScriptGenerator { design -> "// generated ${design.children.size}\n" }
    private val save = SaveScriptUseCase(scripts, bindings, scheduler)

    @Test
    fun `a new design gets a free name and its generated script`() = runTest {
        val name = CreateDesignedScriptUseCase(scripts, generator)(DesignTemplates.Blank.design)

        assertEquals("design_2", name)
        assertEquals(DesignRead.Designed(DesignTemplates.Blank.design), OpenDesignUseCase(scripts)(name))
    }

    @Test
    fun `saving regenerates the code, keeps the icon and refreshes the widgets`() = runTest {
        val first = WidgetDesign(children = listOf(DesignText("a")))
        scripts.files["clock"] = ScriptAppearance("red", "clock").applyTo(DesignHeader.attach(first, generator.generate(first)))
        val second = first.copy(children = first.children + DesignText("b"))

        val result = SaveDesignUseCase(save, scripts, generator)("clock", second)

        assertEquals(ScriptCommandResult.Success("clock"), result)
        val code = scripts.files.getValue("clock")
        assertTrue("// generated 2" in code, code)
        assertEquals(ScriptAppearance("red", "clock"), ScriptAppearance.parse(code))
        assertEquals(DesignRead.Designed(second), OpenDesignUseCase(scripts)("clock"))
        assertEquals(listOf(7), scheduler.refreshed)
    }

    @Test
    fun `plain scripts and missing ones open as plain code`() = runTest {
        assertEquals(DesignRead.Plain, OpenDesignUseCase(scripts)("design_1"))
        assertEquals(DesignRead.Plain, OpenDesignUseCase(scripts)("nope"))
    }

    @Test
    fun `every template has a name and at least one element`() {
        assertTrue(DesignTemplates.all.size >= 5)
        DesignTemplates.all.forEach { assertTrue(it.title.isNotBlank() && it.design.children.isNotEmpty() || it == DesignTemplates.Blank, it.title) }
    }

    @Test
    fun `templates come in English too`() {
        val english = kr.b1ink.scriptwidget.domain.model.DesignTemplates.Battery.design(kr.b1ink.scriptwidget.domain.model.TemplateTexts.ENGLISH)
        val texts = english.children.filterIsInstance<DesignText>().map { it.text }

        assertEquals(listOf("Battery"), texts)
        assertEquals(
            kr.b1ink.scriptwidget.domain.model.DataBinding.DateText("EEEE"),
            (kr.b1ink.scriptwidget.domain.model.DesignTemplates.Clock.design(kr.b1ink.scriptwidget.domain.model.TemplateTexts.ENGLISH).children.first() as DesignText).data,
        )
        assertEquals(DesignTemplates.Battery.design, DesignTemplates.Battery.design(kr.b1ink.scriptwidget.domain.model.TemplateTexts.KOREAN))
    }
}
