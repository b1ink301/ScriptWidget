package kr.b1ink.scriptwidget

import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runs the app's device tests in Korean (the app's per-app language), whatever the device language,
 * so screen tests can look for the Korean texts. English is covered by its own tests.
 * Per-app languages exist from Android 13; older emulators get Korean as their system language
 * from `.github/scripts/use-korean-locale.sh`.
 */
class KoreanLocaleTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            targetContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ko")
        }
        super.onCreate(arguments)
    }
}
