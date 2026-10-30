package kr.b1ink.scriptwidget.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.model.EventTrigger
import kr.b1ink.scriptwidget.domain.model.TriggerAction
import kr.b1ink.scriptwidget.domain.model.TriggerEvent
import kr.b1ink.scriptwidget.ui.triggers.TriggersActions
import kr.b1ink.scriptwidget.ui.triggers.TriggersScreen
import kr.b1ink.scriptwidget.ui.triggers.TriggersUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class TriggersScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = TriggersActions(
        onBack = { calls += "back" },
        onAdd = { event, script, action -> calls += "add:$event:$script:$action" },
        onSetEnabled = { id, on -> calls += "enabled:$id:$on" },
        onRemove = { calls += "remove:$it" },
    )

    @Test
    fun triggersAreListedAndCanBeSwitchedOffOrDeleted() {
        compose.setContent {
            TriggersScreen(
                TriggersUiState(listOf(EventTrigger("a", TriggerEvent.CalendarChanged, "오늘 일정")), listOf("오늘 일정")),
                actions,
            )
        }

        compose.onNodeWithText("캘린더가 바뀌면", substring = true).assertIsDisplayed()
        compose.onNodeWithText("'오늘 일정' 위젯 갱신", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("trigger-switch-a").performClick()
        compose.onNodeWithTag("trigger-remove-a").performClick()

        assertEquals(listOf("enabled:a:false", "remove:a"), calls)
    }

    @Test
    fun aNewTriggerPicksAnEventAScriptAndWhatToDo() {
        compose.setContent { TriggersScreen(TriggersUiState(emptyList(), listOf("오늘 일정", "기록")), actions) }

        compose.onNodeWithTag("add-trigger").performClick()
        compose.onNodeWithTag("event-Charging").performClick()
        compose.onNodeWithTag("script-기록").performClick()
        compose.onNodeWithTag("action-RunScript").performClick()
        compose.onNodeWithTag("confirm-trigger").performClick()

        assertEquals(listOf("add:Charging:기록:RunScript"), calls)
    }
}
