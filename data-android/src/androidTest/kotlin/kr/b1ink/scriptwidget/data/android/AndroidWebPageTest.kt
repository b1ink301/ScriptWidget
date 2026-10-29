package kr.b1ink.scriptwidget.data.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidWebPageTest {
    private val page = AndroidWebPage(ApplicationProvider.getApplicationContext<Context>())

    @After
    fun tearDown() = page.close()

    @Test
    fun evaluatesJavaScriptInLoadedHtml() = runBlocking {
        page.loadHtml("<html><head><title>Hello</title></head><body><h1 id='t'>Headline</h1></body></html>", "https://example.com/")

        assertEquals("\"Hello\"", page.evaluate("document.title", useCallback = false))
        assertEquals("[1,{\"a\":2}]", page.evaluate("[1, {a: 2}]", useCallback = false))
        assertEquals("\"Headline\"", page.evaluate("document.getElementById('t').innerText", useCallback = false))
    }

    @Test
    fun completionCallbacksSupportAsyncCode() = runBlocking {
        page.loadHtml("<p>x</p>", null)

        assertEquals("42", page.evaluate("setTimeout(() => completion(42), 50)", useCallback = true))
    }

    @Test
    fun returnsTheCurrentHtml() = runBlocking {
        page.loadHtml("<html><body><p>content</p></body></html>", null)

        val html = page.html()
        assertTrue("<p>content</p>" in html, html)
    }
}
