package kr.b1ink.scriptwidget.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.ui.main.FloatingTabBar
import kr.b1ink.scriptwidget.ui.main.MainTab
import kr.b1ink.scriptwidget.ui.settings.SettingsActions
import kr.b1ink.scriptwidget.ui.settings.SettingsScreen
import kr.b1ink.scriptwidget.ui.triggers.TriggersActions
import kr.b1ink.scriptwidget.ui.triggers.TriggersScreen
import kr.b1ink.scriptwidget.ui.triggers.TriggersUiState
import kr.b1ink.scriptwidget.ui.text.AppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals

/** The other device tests run in Korean; these draw screens with English resources. */
@RunWith(AndroidJUnit4::class)
class EnglishUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val english: Context = ApplicationProvider.getApplicationContext<Context>().let { base ->
        base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) })
    }

    private fun inEnglish(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(
            LocalContext provides english,
            LocalConfiguration provides english.resources.configuration,
            LocalResources provides english.resources,
        ) { content() }
    }

    @Test
    fun tabsSettingsAndTriggersAreInEnglish() {
        inEnglish {
            Column {
                FloatingTabBar(selected = MainTab.Settings, onSelect = {})
                SettingsScreen(versionName = "1.0", actions = SettingsActions({}, {}, {}, {}, {}, {}, {}))
            }
        }

        compose.onNodeWithText("My scripts").assertIsDisplayed()
        // The tab label and the screen title.
        compose.onAllNodesWithText("Settings").assertCountEquals(2)
        compose.onNodeWithText("Refresh while the screen is off").assertExists()
    }

    @Test
    fun theLicenseRowOpensTheEnglishReadme() {
        val opened = mutableListOf<String>()
        inEnglish { SettingsScreen(versionName = "1.0", actions = SettingsActions({}, {}, {}, {}, {}, {}, { opened += it })) }

        compose.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasText("License"))
        compose.onNodeWithText("License").performClick()

        kotlin.test.assertEquals(listOf("https://github.com/b1ink301/ScriptWidget#license"), opened)
    }

    @Test
    fun triggerScreenIsInEnglish() {
        inEnglish { TriggersScreen(TriggersUiState(), TriggersActions({}, { _, _, _ -> }, { _, _ -> }, {})) }

        compose.onNodeWithText("Automation triggers").assertIsDisplayed()
    }

    @Test
    fun designerTemplatesAndGeneratedScriptsFollowTheLanguage() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.current(english))
        assertEquals(kr.b1ink.scriptwidget.domain.model.TemplateTexts.ENGLISH, AppLanguage.templateTexts(english))
        assertEquals(kr.b1ink.scriptwidget.data.design.GeneratorTexts.ENGLISH, AppLanguage.generatorTexts(english))
    }
}
