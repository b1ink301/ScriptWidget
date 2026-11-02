package kr.b1ink.scriptwidget.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kr.b1ink.scriptwidget.widget.state.drawEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DrawEachTest {

    @Test
    fun aFailingWidgetDoesNotStopTheOthers() = runBlocking {
        val drawn = mutableListOf<Int>()
        val failures = drawEach(listOf(1, 2, 3)) { id ->
            if (id == 1) error("boom")
            drawn += id
        }
        assertEquals(listOf(2, 3), drawn)
        assertEquals(setOf(1), failures.keys)
    }

    @Test
    fun noFailuresWhenEveryWidgetDraws() = runBlocking {
        assertTrue(drawEach(listOf(1, 2)) {}.isEmpty())
    }

    @Test
    fun cancellationIsNotSwallowed() {
        assertFailsWith<CancellationException> {
            runBlocking { drawEach(listOf(1, 2)) { throw CancellationException("stop") } }
        }
    }
}
