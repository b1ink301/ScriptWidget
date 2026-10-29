package kr.b1ink.scriptwidget.data.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BitmapImageStoreTest {
    private val root = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "image-store-test")
    private val store = BitmapImageStore(root, maxDimension = 512)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test
    fun storesSmallImagesAsIs() {
        val image = store.store("widget-1/100", 0, png(40, 30))

        assertEquals(40, image.width)
        assertEquals(30, image.height)
        assertTrue(File(image.src).isFile)
        assertTrue(image.src.startsWith(File(root, "widget-1/100").absolutePath))
    }

    @Test
    fun downsamplesLargeImagesKeepingAspectRatio() {
        val image = store.store("preview/1", 0, png(2000, 1000))

        assertEquals(512, image.width)
        assertEquals(256, image.height)
        val decoded = BitmapFactory.decodeFile(image.src)
        assertEquals(512, decoded.width)
        assertEquals(256, decoded.height)
    }

    @Test
    fun rejectsDataThatIsNotAnImage() {
        assertFailsWith<IOException> { store.store("preview/1", 0, "not an image".toByteArray()) }
    }

    @Test
    fun retainOnlyKeepsTheGivenRun() {
        val old = store.store("widget-2/1", 0, png(10, 10))
        val current = store.store("widget-2/2", 0, png(10, 10))
        val otherWidget = store.store("widget-3/1", 0, png(10, 10))

        store.retainOnly("widget-2", "widget-2/2")

        assertFalse(File(old.src).exists())
        assertTrue(File(current.src).exists())
        assertTrue(File(otherWidget.src).exists())
    }

    @Test
    fun clearRemovesARun() {
        val image = store.store("widget-4/1", 0, png(10, 10))

        store.clear("widget-4/1")

        assertFalse(File(image.src).exists())
    }

    @Test
    fun bytesReturnsStoredImagesOnly() {
        val image = store.store("widget-5/1", 0, png(12, 12))

        assertTrue(BitmapFactory.decodeByteArray(store.bytes(image.src)!!, 0, store.bytes(image.src)!!.size).width == 12)
        assertNull(store.bytes("/etc/hosts"))
        assertNull(store.bytes(File(root, "../outside.png").path))
    }

    // --- storage: photos are kept as JPEG, transparent images as PNG ---

    private fun header(path: String) = File(path).inputStream().use { it.readNBytes(4) }

    @Test
    fun opaqueImagesAreStoredAsJpeg() {
        val photo = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(30, 120, 200)) }
        val bytes = ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val image = store.store("widget-1/200", 0, bytes)

        val head = header(image.src)
        assertEquals(0xFF.toByte(), head[0])
        assertEquals(0xD8.toByte(), head[1])
        assertTrue(BitmapFactory.decodeFile(image.src) != null)
    }

    @Test
    fun transparentImagesStayPng() {
        val image = store.store("widget-1/201", 0, png(40, 30)) // fully transparent

        val head = header(image.src)
        assertEquals(0x89.toByte(), head[0])
        assertEquals('P'.code.toByte(), head[1])
    }
}
