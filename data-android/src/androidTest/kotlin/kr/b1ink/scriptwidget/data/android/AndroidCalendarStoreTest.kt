package kr.b1ink.scriptwidget.data.android

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kr.b1ink.scriptwidget.data.engine.EventData
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidCalendarStoreTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val store = AndroidCalendarStore(ApplicationProvider.getApplicationContext<Context>())
    private val created = mutableListOf<String>()
    private val base = 1_900_000_000_000L // 2030-03-17

    @After
    fun tearDown() = created.forEach { runCatching { store.remove(it) } }

    @Test
    fun aWritableDefaultCalendarAlwaysExists() {
        val calendar = store.defaultCalendar()

        assertTrue(calendar.allowsContentModifications)
        assertTrue(store.calendars().any { it.identifier == calendar.identifier })
    }

    @Test
    fun eventsAreSavedQueriedUpdatedAndRemoved() {
        val calendarId = store.defaultCalendar().identifier
        val id = store.save(EventData(calendarId = calendarId, title = "Gym", location = "Club", start = base, end = base + 3_600_000)).also { created += it }

        val found = store.events(base - 1, base + 7_200_000, listOf(calendarId)).single { it.identifier == id }
        assertEquals("Gym", found.title)
        assertEquals("Club", found.location)
        assertEquals(base, found.start)

        store.save(found.copy(title = "Gym!"))
        assertEquals("Gym!", store.events(base - 1, base + 7_200_000, null).single { it.identifier == id }.title)

        store.remove(id)
        assertTrue(store.events(base - 1, base + 7_200_000, null).none { it.identifier == id })
    }

    @Test
    fun recurringEventsAreExpandedIntoOccurrences() {
        val calendarId = store.defaultCalendar().identifier
        val id = store.save(
            EventData(calendarId = calendarId, title = "Daily", start = base, end = base + 1_800_000, rrule = "FREQ=DAILY;COUNT=3")
        ).also { created += it }

        val occurrences = store.events(base - 1, base + 5 * 86_400_000L, listOf(calendarId)).filter { it.identifier == id }
        assertEquals(3, occurrences.size)
        assertEquals("FREQ=DAILY;COUNT=3", occurrences.first().rrule)
    }
}
