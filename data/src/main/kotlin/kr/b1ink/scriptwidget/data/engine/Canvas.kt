package kr.b1ink.scriptwidget.data.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A rectangle in canvas points. `w`/`h` are null when only a point is given (e.g. `drawText(text, point)`). */
@Serializable
data class Box(val x: Float, val y: Float, val w: Float? = null, val h: Float? = null)

/** A `DrawContext` recorded in JS: size in points, pixel [scale], and the drawing operations in order. */
@Serializable
data class CanvasSpec(
    val width: Float,
    val height: Float,
    val scale: Float = 1f,
    /** Opaque canvases start black, like Scriptable's. */
    val opaque: Boolean = true,
    val ops: List<CanvasOp>,
)

@Serializable
sealed interface PathSegment {
    @Serializable @SerialName("move") data class Move(val x: Float, val y: Float) : PathSegment
    @Serializable @SerialName("line") data class Line(val x: Float, val y: Float) : PathSegment
    @Serializable @SerialName("quad") data class Quad(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathSegment
    @Serializable @SerialName("curve")
    data class Curve(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x: Float, val y: Float) : PathSegment
    @Serializable @SerialName("rect") data class AddRect(val rect: Box) : PathSegment
    @Serializable @SerialName("roundedRect") data class RoundedRect(val rect: Box, val rx: Float, val ry: Float) : PathSegment
    @Serializable @SerialName("ellipse") data class Ellipse(val rect: Box) : PathSegment
    @Serializable @SerialName("close") data object Close : PathSegment
}

@Serializable
sealed interface CanvasOp {
    @Serializable @SerialName("fillColor") data class FillColor(val color: String) : CanvasOp
    @Serializable @SerialName("strokeColor") data class StrokeColor(val color: String) : CanvasOp
    @Serializable @SerialName("lineWidth") data class LineWidth(val width: Float) : CanvasOp
    @Serializable @SerialName("font") data class Font(val size: Float, val weight: String) : CanvasOp
    @Serializable @SerialName("textColor") data class TextColor(val color: String) : CanvasOp
    @Serializable @SerialName("textAlign") data class TextAlign(val align: String) : CanvasOp
    @Serializable @SerialName("fillRect") data class FillRect(val rect: Box) : CanvasOp
    @Serializable @SerialName("fillEllipse") data class FillEllipse(val rect: Box) : CanvasOp
    @Serializable @SerialName("strokeRect") data class StrokeRect(val rect: Box) : CanvasOp
    @Serializable @SerialName("strokeEllipse") data class StrokeEllipse(val rect: Box) : CanvasOp
    @Serializable @SerialName("fillPath") data class FillPath(val segments: List<PathSegment>) : CanvasOp
    @Serializable @SerialName("strokePath") data class StrokePath(val segments: List<PathSegment>) : CanvasOp
    @Serializable @SerialName("text") data class Text(val text: String, val rect: Box) : CanvasOp
    @Serializable @SerialName("image") data class DrawImage(val src: String, val rect: Box) : CanvasOp
}

val CanvasJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Renders a recorded [CanvasSpec] to PNG bytes (backs `DrawContext.getImage()`). */
fun interface CanvasRenderer {
    /** @param imageBytes raw bytes of images referenced by [CanvasOp.DrawImage]. */
    fun render(spec: CanvasSpec, imageBytes: (String) -> ByteArray?): ByteArray
}
