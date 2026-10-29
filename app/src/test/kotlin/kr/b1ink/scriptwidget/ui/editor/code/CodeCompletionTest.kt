package kr.b1ink.scriptwidget.ui.editor.code

import kr.b1ink.scriptwidget.data.api.ApiEntry
import kr.b1ink.scriptwidget.data.api.ApiMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeCompletionTest {
    private val catalog = listOf(
        ApiEntry(
            "ListWidget", ApiEntry.Kind.Class, constructorParams = emptyList(),
            members = listOf(
                ApiMember("addText", ApiMember.Kind.Method, params = listOf("text")),
                ApiMember("addStack", ApiMember.Kind.Method),
                ApiMember("backgroundColor", ApiMember.Kind.Property),
            ),
        ),
        ApiEntry(
            "Font", ApiEntry.Kind.Class, constructorParams = listOf("name", "size"),
            members = listOf(
                ApiMember("boldSystemFont", ApiMember.Kind.Method, params = listOf("size"), isStatic = true),
                ApiMember("systemFont", ApiMember.Kind.Method, params = listOf("size"), isStatic = true),
            ),
        ),
        ApiEntry("Request", ApiEntry.Kind.Class, members = listOf(ApiMember("loadJSON", ApiMember.Kind.Method, isAsync = true))),
    )
    private val completion = CodeCompletion(catalog)

    private fun at(code: String) = completion.suggest(code.replace("|", ""), code.indexOf('|'))

    @Test
    fun `static members after a class name`() {
        val result = at("const f = Font.bo|")

        assertEquals("const f = Font.".length, result.replaceFrom)
        assertEquals(listOf("boldSystemFont"), result.items.map { it.label })
        assertEquals("boldSystemFont()", result.items.single().insert)
        assertEquals(15, result.items.single().cursorOffset) // inside the parentheses
    }

    @Test
    fun `instance members follow the type a variable was created with`() {
        val result = at("const w = new ListWidget();\nw.add|")

        // Shorter (usually the common) names come first.
        assertEquals(listOf("addText", "addStack"), result.items.map { it.label })
    }

    @Test
    fun `members of unknown receivers come from every class`() {
        val labels = at("item.load|").items.map { it.label }
        assertEquals(listOf("loadJSON"), labels)
        assertTrue(at("item.load|").items.single().detail!!.contains("await"))
    }

    @Test
    fun `globals, keywords and names declared in the script`() {
        val labels = at("const listing = 1;\nLi|").items.map { it.label }
        assertEquals(listOf("ListWidget"), labels)

        assertEquals(listOf("listing"), at("const listing = 1;\nlis|").items.map { it.label })
        assertTrue("const" in at("co|").items.map { it.label })
    }

    @Test
    fun `no suggestions without a prefix, inside strings or for an exact match`() {
        assertTrue(at("const x = |").items.isEmpty())
        assertTrue(at("log(\"Font.bo|").items.isEmpty())
        assertTrue(at("ListWidget|").items.isEmpty())
    }
}
