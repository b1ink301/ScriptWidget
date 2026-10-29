package kr.b1ink.scriptwidget.data.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "keychain-test-${System.nanoTime()}"

    @After
    fun tearDown() {
        context.deleteSharedPreferences(name)
    }

    @Test
    fun storesReadsAndRemovesValues() {
        val store = KeystoreSecretStore(context, name)

        store.set("token", "abc 한글 ✓")

        assertTrue(store.contains("token"))
        assertEquals("abc 한글 ✓", store.get("token"))
        store.remove("token")
        assertFalse(store.contains("token"))
        assertNull(store.get("token"))
    }

    @Test
    fun valuesSurviveANewInstance() {
        KeystoreSecretStore(context, name).set("k", "v")

        assertEquals("v", KeystoreSecretStore(context, name).get("k"))
    }

    @Test
    fun valuesAreNotStoredInPlainText() {
        KeystoreSecretStore(context, name).set("apiKey", "PLAINTEXT-SECRET-123")

        val prefsFile = File(context.dataDir, "shared_prefs/$name.xml")
        val raw = prefsFile.readText()
        assertFalse("PLAINTEXT-SECRET-123" in raw, raw)
        assertFalse("apiKey" in raw, "key names are hashed too: $raw")
    }
}
