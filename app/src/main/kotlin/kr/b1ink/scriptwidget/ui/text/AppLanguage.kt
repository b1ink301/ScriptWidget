package kr.b1ink.scriptwidget.ui.text

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import kr.b1ink.scriptwidget.data.design.GeneratorTexts
import kr.b1ink.scriptwidget.domain.model.TemplateTexts

/** The app's language: Korean, or English for every other language (the default resources). */
object AppLanguage {
    const val KOREAN = "ko"
    const val ENGLISH = "en"

    /** "ko" or "en", as the app currently shows its text. */
    fun current(context: Context): String =
        if (context.resources.configuration.locales[0].language == KOREAN) KOREAN else ENGLISH

    fun templateTexts(context: Context): TemplateTexts =
        if (current(context) == KOREAN) TemplateTexts.KOREAN else TemplateTexts.ENGLISH

    fun generatorTexts(context: Context): GeneratorTexts =
        if (current(context) == KOREAN) GeneratorTexts.KOREAN else GeneratorTexts.ENGLISH

    /** Per-app language (Android 13+): null follows the system. */
    val canChoose: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun chosen(context: Context): String? {
        if (!canChoose) return null
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].language
    }

    /** [language] "ko" / "en", or null to follow the system. Activities restart in the new language. */
    fun choose(context: Context, language: String?) {
        if (!canChoose) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
    }
}
