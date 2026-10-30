package kr.b1ink.scriptwidget.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.model.DateNode
import kr.b1ink.scriptwidget.domain.model.Gradient
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.widget.render.ScriptWidgetContent
import kr.b1ink.scriptwidget.domain.model.WidgetError
import kr.b1ink.scriptwidget.widget.render.STALE_BADGE
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ScriptWidgetContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun render(state: RenderState?, assertions: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(DpSize(180.dp, 180.dp))
            provideComposable { ScriptWidgetContent(state) }
            assertions()
        }

    @Test
    fun rendersTextsInsideNestedStacks() = render(
        RenderState(
            scriptName = "s",
            tree = WidgetRoot(
                children = listOf(
                    TextNode("Title"),
                    SpacerNode(),
                    StackNode(children = listOf(TextNode("left"), SpacerNode(4f), StackNode(dir = "v", children = listOf(TextNode("deep"))))),
                ),
            ),
        )
    ) {
        onNode(hasText("Title")).assertExists()
        onNode(hasText("left")).assertExists()
        onNode(hasText("deep")).assertExists()
        onNode(hasText(STALE_BADGE)).assertDoesNotExist()
    }

    @Test
    fun rootUrlOpensTheUrl() = render(
        RenderState(scriptName = "s", tree = WidgetRoot(url = "https://example.com", children = listOf(TextNode("x"))))
    ) {
        onNode(hasStartActivityClickAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))).assertExists()
    }

    @Test
    fun elementUrlOpensItsOwnUrl() = render(
        RenderState(scriptName = "s", tree = WidgetRoot(children = listOf(TextNode("link", url = "https://a.b/c"))))
    ) {
        onNode(hasStartActivityClickAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://a.b/c")))).assertExists()
    }

    @Test
    fun rootWithoutUrlIsStillClickableForRefresh() = render(
        RenderState(scriptName = "s", tree = WidgetRoot(children = listOf(TextNode("x"))))
    ) {
        onNode(hasClickAction()).assertExists()
    }

    @Test
    fun failedRefreshKeepsTheTreeAndShowsABadge() = render(
        RenderState(scriptName = "s", tree = WidgetRoot(children = listOf(TextNode("last good"))), error = "offline")
    ) {
        onNode(hasText("last good")).assertExists()
        onNode(hasText(STALE_BADGE)).assertExists()
    }

    @Test
    fun errorWithoutTreeShowsScriptNameAndMessage() = render(
        RenderState(scriptName = "weather", error = "ReferenceError: x")
    ) {
        onNode(hasText("weather")).assertExists()
        onNode(hasText("ReferenceError: x")).assertExists()
    }

    @Test
    fun noStateShowsTheWaitingMessage() = render(null) {
        onNode(hasText(text(R.string.widget_waiting))).assertExists()
    }

    @Test
    fun appErrorsArePutIntoWordsInTheAppLanguage() {
        render(RenderState(error = WidgetError.encode(WidgetError.NotBound))) {
            onNode(hasText(text(R.string.widget_error_not_bound))).assertExists()
        }
        render(RenderState(scriptName = "weather", error = WidgetError.encode(WidgetError.ScriptNotFound("weather")))) {
            onNode(hasText(text(R.string.widget_error_script_not_found, "weather"))).assertExists()
        }
    }

    @Test
    fun dateNodesAreFormattedWithTheDeviceFormat() {
        val time = 1_700_000_000_000L
        val expected = DateFormat.getMediumDateFormat(context).format(Date(time))
        render(RenderState(scriptName = "s", tree = WidgetRoot(children = listOf(DateNode(time = time, style = "date"))))) {
            onNode(hasText(expected)).assertExists()
        }
    }

    @Test
    fun missingImageFilesAreSkipped() = render(
        RenderState(
            scriptName = "s",
            tree = WidgetRoot(children = listOf(ImageNode(src = "/does/not/exist.png"), TextNode("after"))),
        )
    ) {
        onNode(hasText("after")).assertExists()
    }

    @Test
    fun gradientAndImageBackgroundsRenderTheirContent() = render(
        RenderState(
            scriptName = "s",
            tree = WidgetRoot(
                gradient = Gradient(listOf("#FF000000", "#FFFFFFFF")),
                children = listOf(
                    StackNode(backgroundImage = "/missing.png", children = listOf(TextNode("on image"))),
                    TextNode("on gradient"),
                ),
            ),
        )
    ) {
        onNode(hasText("on gradient")).assertExists()
        onNode(hasText("on image")).assertExists()
    }

    @Test
    fun alignedTintedImagesAndBorderedStacksRender() {
        val dir = context.cacheDir
        val png = java.io.File(dir, "render-test.png").apply {
            outputStream().use { android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        render(
            RenderState(
                scriptName = "s",
                tree = WidgetRoot(
                    children = listOf(
                        ImageNode(src = png.path, align = "right", tint = "#FFFF0000", contentMode = "fill"),
                        StackNode(borderColor = "#FF00FF00", borderWidth = 2f, children = listOf(TextNode("bordered"))),
                    ),
                ),
            )
        ) {
            onNode(hasText("bordered")).assertExists()
        }
    }

    @Test
    fun tapActionsRunTheWidgetActionCallback() = render(
        RenderState(
            scriptName = "s",
            tree = WidgetRoot(children = listOf(TextNode("plus", action = "increment"), TextNode("link", url = "https://a.b", action = "wins"))),
        )
    ) {
        onNode(hasText("plus")).assertHasRunCallbackClickAction<WidgetActionCallback>(
            actionParametersOf(WidgetActionCallback.ACTION to "increment")
        )
        // An action takes precedence over a url on the same element.
        onNode(hasText("link")).assertHasRunCallbackClickAction<WidgetActionCallback>(
            actionParametersOf(WidgetActionCallback.ACTION to "wins")
        )
    }
}
