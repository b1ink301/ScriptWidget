package kr.b1ink.scriptwidget.domain.repository

import kr.b1ink.scriptwidget.domain.model.WidgetDesign

/** Turns a design into the body of a Scriptable script (without the design header). */
fun interface DesignScriptGenerator {
    fun generate(design: WidgetDesign): String
}
