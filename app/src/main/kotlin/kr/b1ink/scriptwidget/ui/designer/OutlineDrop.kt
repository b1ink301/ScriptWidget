package kr.b1ink.scriptwidget.ui.designer

/** Where an element dragged in the outline lands, from the rows' positions and the finger's y. */
object OutlineDrop {
    /** An outline row; [stackSize] is the number of children when the row is a stack. */
    data class Row(val path: List<Int>, val top: Float, val bottom: Float, val stackSize: Int? = null)

    enum class Place { Before, After, Inside }

    /** Move into [parent] at [index] (see `WidgetDesign.moveTo`); [row] and [place] draw the marker. */
    data class Target(val parent: List<Int>, val index: Int, val row: List<Int>, val place: Place)

    fun at(rows: List<Row>, dragged: List<Int>, y: Float): Target? {
        if (rows.isEmpty()) return null
        val roots = rows.filter { it.path.size == 1 }
        val row = when {
            y < rows.first().top -> return before(rows.first()).takeUnless { inside(dragged, it.row) }
            y >= rows.last().bottom -> return Target(emptyList(), roots.size, roots.last().path, Place.After).takeUnless { inside(dragged, it.row) }
            else -> rows.firstOrNull { y >= it.top && y < it.bottom } ?: return null
        }
        if (inside(dragged, row.path)) return null
        val third = (row.bottom - row.top) / 3
        return when {
            row.stackSize != null && y >= row.top + third && y < row.bottom - third -> Target(row.path, row.stackSize, row.path, Place.Inside)
            y < (row.top + row.bottom) / 2 && (row.stackSize == null || y < row.top + third) -> before(row)
            else -> Target(row.path.dropLast(1), row.path.last() + 1, row.path, Place.After)
        }
    }

    private fun before(row: Row) = Target(row.path.dropLast(1), row.path.last(), row.path, Place.Before)

    /** [path] is [dragged] or inside it. */
    private fun inside(dragged: List<Int>, path: List<Int>) = path.size >= dragged.size && path.take(dragged.size) == dragged
}
