package kr.b1ink.scriptwidget.data.android

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Rule
import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AndroidPhotoLibraryTest {
    /** Photo access is READ_MEDIA_IMAGES from Android 13, storage permissions before (writing too before 10). */
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        *when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        },
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val library = AndroidPhotoLibrary(context)

    @After
    fun tearDown() = library.deleteSavedByThisApp()

    private fun png(color: Int, size: Int) = ByteArrayOutputStream().also {
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    @Test
    fun savedImagesAreTheLatestPhotos() {
        library.save(png(Color.RED, 37))

        val latest = library.latest(1, screenshots = false).single()
        val bitmap = BitmapFactory.decodeByteArray(latest, 0, latest.size)
        assertEquals(37, bitmap.width)
        assertEquals(Color.RED, bitmap.getPixel(5, 5))
    }

    @Test
    fun screenshotsAreFilteredByFolder() {
        library.save(png(Color.BLUE, 23), relativePath = "Pictures/Screenshots/")
        library.save(png(Color.GREEN, 11))

        val screenshot = library.latest(1, screenshots = true).single()
        assertEquals(23, BitmapFactory.decodeByteArray(screenshot, 0, screenshot.size).width)
    }
}
