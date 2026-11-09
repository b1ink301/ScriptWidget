package kr.b1ink.scriptwidget.ui.permissions

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The photo permission differs by Android version (asked for in the editor and the permissions screen). */
class PermissionsForVersionTest {
    @Test
    fun `photos use READ_MEDIA_IMAGES from Android 13`() {
        assertEquals(listOf(Manifest.permission.READ_MEDIA_IMAGES), forThisVersion(Manifest.permission.READ_MEDIA_IMAGES, sdkInt = 33))
    }

    @Test
    fun `photos use storage reading on Android 10 to 12`() {
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), forThisVersion(Manifest.permission.READ_MEDIA_IMAGES, sdkInt = 29))
    }

    @Test
    fun `saving photos before Android 10 also needs storage writing`() {
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            forThisVersion(Manifest.permission.READ_MEDIA_IMAGES, sdkInt = 26),
        )
    }

    @Test
    fun `other permissions are the same everywhere`() {
        assertEquals(listOf(Manifest.permission.READ_CALENDAR), forThisVersion(Manifest.permission.READ_CALENDAR, sdkInt = 26))
    }
}
