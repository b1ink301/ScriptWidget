package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetDesignTest {
    private val title = DesignText("제목")
    private val clock = DesignDate(style = "time")
    private val row = DesignStack(dir = "h", children = listOf(DesignSymbol("battery.100"), DesignText("{value}", data = DataBinding.Battery)))
    private val design = WidgetDesign(children = listOf(title, row, clock))

    @Test
    fun `elements are found by their path`() {
        assertEquals(title, design.elementAt(listOf(0)))
        assertEquals(DesignSymbol("battery.100"), design.elementAt(listOf(1, 0)))
        assertNull(design.elementAt(listOf(5)))
        assertNull(design.elementAt(listOf(0, 0)))
    }

    @Test
    fun `adding goes into the selected stack, after the selected element, or at the end`() {
        val spacer = DesignSpacer()

        assertEquals(listOf(1, 2), design.add(spacer, selected = listOf(1)).second)
        assertEquals(spacer, design.add(spacer, selected = listOf(1)).first.elementAt(listOf(1, 2)))
        assertEquals(listOf(1), design.add(spacer, selected = listOf(0)).second)
        assertEquals(listOf(1, 1), design.add(spacer, selected = listOf(1, 0)).second)
        assertEquals(listOf(3), design.add(spacer, selected = null).second)
    }

    @Test
    fun `elements are updated, removed and moved within their parent`() {
        val updated = design.update(listOf(1, 1)) { (it as DesignText).copy(size = 30f) }
        assertEquals(30f, (updated.elementAt(listOf(1, 1)) as DesignText).size)

        assertEquals(listOf(row, clock), design.remove(listOf(0)).children)

        val (moved, path) = design.move(listOf(2), by = -1)
        assertEquals(listOf(title, clock, row), moved.children)
        assertEquals(listOf(1), path)
        assertEquals(design to listOf(0), design.move(listOf(0), by = -1))
        assertEquals(listOf(1, 1), design.move(listOf(1, 0), by = 1).second)
    }

    @Test
    fun `moving to a place among the siblings counts the place before the element is taken out`() {
        val (later, path) = design.moveTo(listOf(0), toParent = emptyList(), index = 3)
        assertEquals(listOf(row, clock, title), later.children)
        assertEquals(listOf(2), path)

        val (earlier, back) = design.moveTo(listOf(2), toParent = emptyList(), index = 0)
        assertEquals(listOf(clock, title, row), earlier.children)
        assertEquals(listOf(0), back)

        assertEquals(design to listOf(1), design.moveTo(listOf(1), toParent = emptyList(), index = 2))
    }

    @Test
    fun `elements move into and out of stacks`() {
        val (inside, path) = design.moveTo(listOf(0), toParent = listOf(1), index = 2)
        assertEquals(listOf(row.copy(children = row.children + title), clock), inside.children)
        assertEquals(listOf(0, 2), path)

        val (outside, out) = design.moveTo(listOf(1, 0), toParent = emptyList(), index = 3)
        assertEquals(listOf(title, row.copy(children = row.children.drop(1)), clock, DesignSymbol("battery.100")), outside.children)
        assertEquals(listOf(3), out)
    }

    @Test
    fun `a stack after the moved element keeps receiving it at its new path`() {
        val (moved, path) = design.moveTo(listOf(0), toParent = listOf(1), index = 0)

        assertEquals(title, moved.elementAt(listOf(0, 0)))
        assertEquals(listOf(0, 0), path)
    }

    @Test
    fun `a stack cannot move into itself and the target must be a stack`() {
        val nested = WidgetDesign(children = listOf(DesignStack(children = listOf(DesignStack(), title))))

        assertEquals(nested to listOf(0), nested.moveTo(listOf(0), toParent = listOf(0), index = 0))
        assertEquals(nested to listOf(0), nested.moveTo(listOf(0), toParent = listOf(0, 0), index = 0))
        assertEquals(design to listOf(2), design.moveTo(listOf(2), toParent = listOf(0), index = 0))
        assertEquals(design to listOf(2), design.moveTo(listOf(2), toParent = emptyList(), index = 9))
    }

    @Test
    fun `a stack can be wrapped around an element`() {
        val (wrapped, path) = design.wrapInStack(listOf(0), dir = "v")

        assertEquals(DesignStack(dir = "v", children = listOf(title)), wrapped.elementAt(listOf(0)))
        assertEquals(listOf(0), path)
    }

    @Test
    fun `the bindings in use are listed once`() {
        val withMore = design.add(DesignText("{value}", data = DataBinding.Battery), null).first
            .add(DesignText("{value}", data = DataBinding.Json("https://a.b/x.json", "data.0.name")), null).first

        assertEquals(listOf(DataBinding.Battery, DataBinding.Json("https://a.b/x.json", "data.0.name")), withMore.bindings())
    }

    @Test
    fun `designs survive a json round trip`() {
        val full = design.copy(
            background = DesignBackground(color = "#FF101820", gradient = listOf("#FF203040", "#FF000000")),
            refreshMinutes = 30,
            children = design.children + DesignImage(url = "https://example.com/a.png", size = 40f) +
                DesignText("{value}°", data = DataBinding.Weather(DataBinding.Weather.Field.Temperature)),
        )

        assertEquals(full, WidgetDesign.decode(full.encode()))
        assertTrue('\n' !in full.encode())
    }

    @Test
    fun `images can come from a file bookmark, and older designs still read`() {
        val design = WidgetDesign(
            background = DesignBackground(image = "wallpaper"),
            children = listOf(DesignImage(bookmark = "photo"), DesignStack(children = listOf(DesignImage(bookmark = "photo"), DesignImage("https://a.b/c.png")))),
        )

        assertEquals(design, WidgetDesign.decode(design.encode()))
        assertEquals(listOf("wallpaper", "photo"), design.bookmarks())
        assertEquals(
            WidgetDesign(children = listOf(DesignImage("https://a.b/c.png", size = 40f))),
            WidgetDesign.decode("""{"children":[{"type":"image","url":"https://a.b/c.png","size":40.0}]}"""),
        )
    }
}
