package kr.b1ink.scriptwidget.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClasspathSampleScriptSourceTest {

    @Test
    fun `loads every bundled sample listed in the index`() {
        val samples = ClasspathSampleScriptSource().load()

        assertEquals(
            listOf("01_hello", "02_exchange_rate", "03_image", "04_error", "05_async_timers", "06_weather", "07_showcase", "08_settings_ui", "09_counter"),
            samples.keys.sorted(),
        )
        assertTrue(samples.values.all { it.isNotBlank() })
    }
}
