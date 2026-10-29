package kr.b1ink.scriptwidget.data.symbols

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SfSymbolsTest {

    @Test
    fun `common SF Symbols map to Material Icons`() {
        assertEquals("wb_sunny", SfSymbols.resolve("sun.max").materialName)
        assertEquals("wb_sunny", SfSymbols.resolve("sun.max.fill").materialName)
        assertEquals("local_gas_station", SfSymbols.resolve("fuelpump.circle").materialName)
        assertEquals("south_east", SfSymbols.resolve("arrow.down.right").materialName)
        assertEquals("apps", SfSymbols.resolve("square.grid.4x3.fill").materialName)
        assertEquals("cloud", SfSymbols.resolve("cloud.fill").materialName)
    }

    @Test
    fun `filled and circled variants fall back to their base symbol`() {
        val resolved = SfSymbols.resolve("heart.circle.fill")
        assertEquals("favorite", resolved.materialName)
        assertEquals(false, resolved.isFallback)
    }

    @Test
    fun `Material icon names can be used directly`() {
        assertEquals("rocket_launch", SfSymbols.resolve("rocket_launch").materialName)
    }

    @Test
    fun `unknown symbols fall back to a placeholder`() {
        val resolved = SfSymbols.resolve("definitely.not.a.symbol")
        assertEquals(SfSymbols.FALLBACK, resolved.materialName)
        assertTrue(resolved.isFallback)
    }

    @Test
    fun `every mapped Material name exists in the bundled font`() {
        val missing = SfSymbols.mappedMaterialNames().filter { MaterialIcons.codepointOf(it) == null }
        assertEquals(emptyList(), missing)
        assertNotNull(MaterialIcons.codepointOf(SfSymbols.FALLBACK))
    }

    @Test
    fun `the mapping covers a useful set of symbols`() {
        assertTrue(SfSymbols.mappedSymbolCount() >= 150, "${SfSymbols.mappedSymbolCount()}")
    }
}
