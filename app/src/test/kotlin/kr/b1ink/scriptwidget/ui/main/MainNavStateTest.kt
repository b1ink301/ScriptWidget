package kr.b1ink.scriptwidget.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainNavStateTest {
    private val home = MainNavState()

    @Test
    fun `starts on my scripts with the tab bar visible`() {
        assertEquals(MainTab.Scripts, home.tab)
        assertTrue(home.showsTabBar)
    }

    @Test
    fun `back on another tab returns to my scripts, back on my scripts leaves the app`() {
        val gallery = home.select(MainTab.Gallery)

        assertEquals(home, gallery.back())
        assertNull(home.back())
    }

    @Test
    fun `an opened script hides the tab bar and closing it returns to the same tab`() {
        val editing = home.select(MainTab.Gallery).open("오늘 일정")

        assertFalse(editing.showsTabBar)
        assertEquals("오늘 일정", editing.editing)
        assertEquals(MainTab.Gallery, editing.closeEditor().tab)
        assertEquals(editing.closeEditor(), editing.back())
    }

    @Test
    fun `a full screen over the editor goes back to the editor`() {
        val api = home.open("clock").show(Overlay.ApiReference)

        assertFalse(api.showsTabBar)
        assertEquals(home.open("clock"), api.back())
    }

    @Test
    fun `settings pages go back to settings`() {
        val settings = home.select(MainTab.Settings)

        assertEquals(settings, settings.show(Overlay.Permissions).back())
        assertEquals(settings, settings.show(Overlay.Bookmarks).back())
    }

    @Test
    fun `selecting a tab closes anything on top`() {
        val state = home.select(MainTab.Settings).show(Overlay.Bookmarks).select(MainTab.History)

        assertEquals(MainNavState(tab = MainTab.History), state)
    }

    @Test
    fun `a home link closes the editor and full screens but keeps the tab`() {
        val state = home.select(MainTab.History).open("a").show(Overlay.ApiReference).goHome()

        assertEquals(MainNavState(tab = MainTab.History), state)
    }

    @Test
    fun `incoming files show my scripts`() {
        val state = home.select(MainTab.Gallery).open("a").showScripts()

        assertEquals(home, state)
    }

    @Test
    fun `import, backup and restore from settings run on my scripts`() {
        val state = home.select(MainTab.Settings).request(ListAction.Import)

        assertEquals(MainTab.Scripts, state.tab)
        assertEquals(ListAction.Import, state.listAction)
        assertNull(state.actionDone().listAction)
    }

    @Test
    fun `the state survives being saved`() {
        val state = home.select(MainTab.Gallery).open("오늘 일정").show(Overlay.ApiReference)

        assertEquals(state, MainNavState.restore(state.save()))
        assertEquals(home, MainNavState.restore(home.save()))
    }

    @Test
    fun `a designed script opens in the designer and can switch to its code and back`() {
        val designing = home.open("design_1", designer = true)

        assertTrue(designing.designing)
        assertFalse(designing.showsTabBar)
        assertFalse(designing.showCode().designing)
        assertEquals("design_1", designing.showCode().editing)
        assertTrue(designing.showCode().showDesigner().designing)
        assertEquals(home, designing.back())
        assertFalse(home.open("plain").designing)
    }

    @Test
    fun `the designer survives being saved and restored`() {
        val designing = home.select(MainTab.Gallery).open("design_1", designer = true)

        assertEquals(designing, MainNavState.restore(designing.save()))
    }
}
