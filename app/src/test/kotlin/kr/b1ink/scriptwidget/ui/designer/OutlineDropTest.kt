package kr.b1ink.scriptwidget.ui.designer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutlineDropTest {
    // Rows 30px high: [0] text, [1] stack with [1,0] and [1,1], [2] text.
    private val rows = listOf(
        OutlineDrop.Row(listOf(0), top = 0f, bottom = 30f),
        OutlineDrop.Row(listOf(1), top = 30f, bottom = 60f, stackSize = 2),
        OutlineDrop.Row(listOf(1, 0), top = 60f, bottom = 90f),
        OutlineDrop.Row(listOf(1, 1), top = 90f, bottom = 120f),
        OutlineDrop.Row(listOf(2), top = 120f, bottom = 150f),
    )

    private fun drop(dragged: List<Int>, y: Float) = OutlineDrop.at(rows, dragged, y)

    @Test
    fun `the top and bottom of a row put the element before or after it`() {
        assertEquals(OutlineDrop.Target(emptyList(), 2, listOf(2), OutlineDrop.Place.Before), drop(listOf(0), 125f))
        assertEquals(OutlineDrop.Target(emptyList(), 3, listOf(2), OutlineDrop.Place.After), drop(listOf(0), 145f))
        assertEquals(OutlineDrop.Target(listOf(1), 1, listOf(1, 1), OutlineDrop.Place.Before), drop(listOf(2), 92f))
    }

    @Test
    fun `the middle of a stack row puts the element inside it, at the end`() {
        assertEquals(OutlineDrop.Target(listOf(1), 2, listOf(1), OutlineDrop.Place.Inside), drop(listOf(0), 45f))
    }

    @Test
    fun `the middle of a plain row picks the nearer side`() {
        assertEquals(OutlineDrop.Target(emptyList(), 0, listOf(0), OutlineDrop.Place.Before), drop(listOf(2), 14f))
        assertEquals(OutlineDrop.Target(emptyList(), 1, listOf(0), OutlineDrop.Place.After), drop(listOf(2), 16f))
    }

    @Test
    fun `above and below every row are the ends of the widget`() {
        assertEquals(OutlineDrop.Target(emptyList(), 0, listOf(0), OutlineDrop.Place.Before), drop(listOf(2), -40f))
        assertEquals(OutlineDrop.Target(emptyList(), 3, listOf(2), OutlineDrop.Place.After), drop(listOf(0), 400f))
    }

    @Test
    fun `an element cannot be dropped on itself or inside its own stack`() {
        assertNull(drop(listOf(1), 45f))
        assertNull(drop(listOf(1), 70f))
        assertNull(drop(listOf(0), 10f))
    }
}
