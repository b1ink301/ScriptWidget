package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogValidatorTest {
    private fun entry(id: String, vararg permissions: String, sizes: List<String> = listOf("small"), name: String = id, category: String = "정보") =
        CatalogEntry(id = id, name = name, category = category, description = "설명", script = "$id.js", sizes = sizes, permissions = permissions.toList())

    private fun problems(vararg entries: Pair<CatalogEntry, String?>) =
        CatalogValidator.validate(Catalog("t", entries.map { it.first }), code = entries.associate { it.first.id to it.second }::get)

    @Test
    fun `a good catalog has no problems`() {
        assertEquals(
            emptyList(),
            problems(entry("clock") to "const w = new ListWidget(); Script.setWidget(w);", entry("weather", "location", "network") to "await Location.current(); await new Request(u).loadJSON();"),
        )
    }

    @Test
    fun `apis the script uses must be declared as permissions`() {
        val found = problems(entry("events") to "const e = await CalendarEvent.today([]); const r = new Request(u);")

        assertEquals(
            listOf(
                CatalogProblem.Error("events", "uses calendar but does not declare it in permissions"),
                CatalogProblem.Error("events", "uses network but does not declare it in permissions"),
            ),
            found,
        )
    }

    @Test
    fun `declared permissions the script never uses are warnings`() {
        assertEquals(listOf(CatalogProblem.Warning("quote", "declares contacts but does not use it")), problems(entry("quote", "contacts") to "Script.complete()"))
    }

    @Test
    fun `entry fields are checked`() {
        val found = problems(
            entry("a", sizes = listOf("huge")) to "x",
            entry("a") to "x",
            entry("b", name = "bad/name") to "x",
            entry("c", "teleport") to "x",
            CatalogEntry(id = "d", name = "d", script = "d.js") to null,
        ).filterIsInstance<CatalogProblem.Error>().map { it.entryId to it.message }

        assertTrue("a" to "unknown size \"huge\"" in found, found.toString())
        assertTrue("a" to "duplicate id" in found, found.toString())
        assertTrue("b" to "name \"bad/name\" is not a valid script name" in found, found.toString())
        assertTrue("c" to "unknown permission \"teleport\"" in found, found.toString())
        assertTrue("d" to "script d.js not found" in found, found.toString())
        assertTrue("d" to "no sizes" in found, found.toString())
        assertTrue("d" to "no description" in found, found.toString())
    }

    @Test
    fun `the permissions a script needs are read from its code`() {
        assertEquals(setOf("location", "network"), CatalogValidator.usedPermissions("await Location.current(); new Request(u)"))
        assertEquals(emptySet(), CatalogValidator.usedPermissions("Script.complete()"))
    }
}
