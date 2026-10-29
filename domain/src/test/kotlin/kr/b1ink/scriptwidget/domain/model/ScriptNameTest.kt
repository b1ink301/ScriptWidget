package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptNameTest {

    @Test
    fun `accepts ordinary names`() {
        listOf("hello", "02_exchange_rate", "날씨 위젯", "my-script.v2").forEach {
            assertTrue(ScriptName.isValid(it), it)
        }
    }

    @Test
    fun `rejects blank, path-like, hidden and too long names`() {
        listOf("", "   ", "a/b", "a\\b", ".hidden", "..", "x".repeat(65)).forEach {
            assertFalse(ScriptName.isValid(it), it)
        }
    }
}
