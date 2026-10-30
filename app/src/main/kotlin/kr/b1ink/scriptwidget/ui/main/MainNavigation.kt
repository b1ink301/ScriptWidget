package kr.b1ink.scriptwidget.ui.main

/** The floating tab bar's tabs, in order. */
enum class MainTab { Scripts, Gallery, History, Help, Settings }

/** Full screens shown over a tab or the editor (the tab bar is hidden). */
enum class Overlay { Permissions, Bookmarks, ApiReference }

/** Settings actions that run on "my scripts", where their result shows. */
enum class ListAction { Import, Backup, Restore }

/**
 * What the main screen shows: a tab, the editor over it, and a full screen over either.
 * Pure, so the navigation rules are unit-tested; closing anything returns to where it was opened.
 */
data class MainNavState(
    val tab: MainTab = MainTab.Scripts,
    val editing: String? = null,
    val overlay: Overlay? = null,
    val listAction: ListAction? = null,
    /** The opened script is shown in the widget designer instead of the code editor. */
    val designing: Boolean = false,
) {
    val showsTabBar: Boolean get() = editing == null && overlay == null

    fun select(tab: MainTab) = MainNavState(tab = tab)

    fun open(scriptName: String, designer: Boolean = false) = copy(editing = scriptName, overlay = null, designing = designer)

    fun showCode() = copy(designing = false)

    fun showDesigner() = copy(designing = true)

    fun closeEditor() = copy(editing = null, overlay = null, designing = false)

    fun show(overlay: Overlay) = copy(overlay = overlay)

    /** A `scriptwidget://` home link. */
    fun goHome() = copy(editing = null, overlay = null, designing = false)

    /** Files were shared with or opened in the app: they are imported into "my scripts". */
    fun showScripts() = MainNavState()

    fun request(action: ListAction) = MainNavState(listAction = action)

    fun actionDone() = copy(listAction = null)

    /** The state after "back", or null when back should leave the app. */
    fun back(): MainNavState? = when {
        overlay != null -> copy(overlay = null)
        editing != null -> copy(editing = null, designing = false)
        tab != MainTab.Scripts -> MainNavState()
        else -> null
    }

    fun save(): List<String?> = listOf(tab.name, editing, overlay?.name, listAction?.name, designing.toString())

    companion object {
        fun restore(saved: List<String?>) = MainNavState(
            tab = saved.getOrNull(0)?.let(MainTab::valueOf) ?: MainTab.Scripts,
            editing = saved.getOrNull(1),
            overlay = saved.getOrNull(2)?.let(Overlay::valueOf),
            listAction = saved.getOrNull(3)?.let(ListAction::valueOf),
            designing = saved.getOrNull(4).toBoolean(),
        )
    }
}
