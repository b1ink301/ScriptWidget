package kr.b1ink.scriptwidget.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkTest {

    @Test
    fun `parses run, open and settings links`() {
        assertEquals(DeepLink.Run("My Script", emptyMap()), DeepLink.parse("scriptwidget://run?scriptName=My%20Script"))
        assertEquals(DeepLink.Open("weather"), DeepLink.parse("scriptwidget://open?scriptName=weather"))
        assertEquals(DeepLink.Open("weather"), DeepLink.parse("scriptwidget://settings?scriptName=weather"))
        assertEquals(DeepLink.Home, DeepLink.parse("scriptwidget://"))
    }

    @Test
    fun `other query parameters are passed to the script`() {
        assertEquals(
            DeepLink.Run("w", mapOf("city" to "Seoul", "unit" to "c")),
            DeepLink.parse("scriptwidget://run?scriptName=w&city=Seoul&unit=c"),
        )
    }

    @Test
    fun `rejects foreign or incomplete links`() {
        assertNull(DeepLink.parse("https://example.com"))
        assertNull(DeepLink.parse("scriptwidget://run"))
        assertNull(DeepLink.parse("scriptwidget://unknown?scriptName=x"))
    }

    @Test
    fun `parses x-callback-url replies`() {
        assertEquals(
            DeepLink.Callback(status = "success", requestId = "R1", params = mapOf("text" to "done ok")),
            DeepLink.parse("scriptwidget://x-callback-url/success?requestId=R1&text=done%20ok"),
        )
        assertEquals(DeepLink.Callback("cancel", "R2", emptyMap()), DeepLink.parse("scriptwidget://x-callback-url/cancel?requestId=R2"))
        assertNull(DeepLink.parse("scriptwidget://x-callback-url/success"))
    }

    // --- Scriptable's own links (scripts written for iOS open them with Safari.open / widget urls) ---

    @Test
    fun `scriptable links with the script in the path`() {
        assertEquals(DeepLink.Run("Pixel Widget v3.1", emptyMap()), DeepLink.parse("scriptable:///run/Pixel%20Widget%20v3.1"))
        assertEquals(DeepLink.Open("weather"), DeepLink.parse("scriptable:///open/weather"))
    }

    @Test
    fun `scriptable links with query parameters`() {
        assertEquals(
            DeepLink.Run("sub-calendar", mapOf("action" to "subPreference", "targetIdentifier" to "42")),
            DeepLink.parse("scriptable:///run?scriptName=sub-calendar&action=subPreference&targetIdentifier=42"),
        )
        assertEquals(DeepLink.Run("a", mapOf("x" to "1")), DeepLink.parse("scriptable:///run/a?x=1"))
    }

    @Test
    fun `scriptable app link opens the app`() {
        assertEquals(DeepLink.Home, DeepLink.parse("scriptable:///"))
        assertEquals(DeepLink.Home, DeepLink.parse("scriptable://"))
        assertNull(DeepLink.parse("scriptable:///run"))
    }
}
