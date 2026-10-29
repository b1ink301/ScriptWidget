package kr.b1ink.scriptwidget.widget.render

/** `Color.dynamic(light, dark)` arrives as "#light/#dark"; a plain color is the same in both themes. */
object ThemedColor {
    fun split(value: String): Pair<String, String> {
        val light = value.substringBefore('/')
        val dark = value.substringAfter('/', light)
        return light to dark
    }

    fun pick(value: String, night: Boolean): String = split(value).let { if (night) it.second else it.first }
}
