package kr.b1ink.scriptwidget.data.android

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import kr.b1ink.scriptwidget.data.engine.CalendarInfo
import kr.b1ink.scriptwidget.data.engine.CalendarStore
import kr.b1ink.scriptwidget.data.engine.EventData
import java.util.TimeZone

/** [CalendarStore] over CalendarContract. Creates a local "ScriptWidget" calendar when no writable one exists. */
class AndroidCalendarStore(context: Context) : CalendarStore {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    override fun calendars(): List<CalendarInfo> = guarded {
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL),
            "${Calendars.VISIBLE} = 1", null, null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CalendarInfo(
                            identifier = c.getLong(0).toString(),
                            title = c.getString(1).orEmpty(),
                            color = "#%08X".format(c.getInt(2)),
                            allowsContentModifications = c.getInt(3) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                        )
                    )
                }
            }
        }.orEmpty()
    }

    override fun defaultCalendar(): CalendarInfo = guarded {
        val writable = calendars().filter { it.allowsContentModifications }
        val primary = resolver.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID), "${Calendars.IS_PRIMARY} = 1", null, null)
            ?.use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0).toString()) } }.orEmpty()
        writable.firstOrNull { it.identifier in primary } ?: writable.firstOrNull() ?: createLocalCalendar()
    }

    private fun createLocalCalendar(): CalendarInfo {
        val uri = Calendars.CONTENT_URI.asSyncAdapter()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, LOCAL_ACCOUNT)
            put(Calendars.CALENDAR_DISPLAY_NAME, LOCAL_ACCOUNT)
            put(Calendars.CALENDAR_COLOR, 0xFF5856D6.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val id = ContentUris.parseId(requireNotNull(resolver.insert(uri, values)) { "Could not create a calendar" })
        return CalendarInfo(id.toString(), LOCAL_ACCOUNT, "#FF5856D6", allowsContentModifications = true)
    }

    override fun events(start: Long, end: Long, calendarIds: List<String>?): List<EventData> = guarded {
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val selection = calendarIds?.let { ids -> "${Instances.CALENDAR_ID} IN (${ids.joinToString(",") { "?" }})" }
        resolver.query(
            uri,
            arrayOf(
                Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.EVENT_LOCATION, Instances.DESCRIPTION,
                Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.EVENT_TIMEZONE, Instances.RRULE, Instances.AVAILABILITY,
            ),
            selection, calendarIds?.toTypedArray(), "${Instances.BEGIN} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        EventData(
                            identifier = c.getLong(0).toString(),
                            calendarId = c.getLong(1).toString(),
                            title = c.getString(2).orEmpty(),
                            location = c.getString(3)?.takeIf { it.isNotEmpty() },
                            notes = c.getString(4)?.takeIf { it.isNotEmpty() },
                            start = c.getLong(5),
                            end = c.getLong(6),
                            allDay = c.getInt(7) == 1,
                            timeZone = c.getString(8),
                            rrule = c.getString(9),
                            availability = when (c.getInt(10)) {
                                Events.AVAILABILITY_FREE -> "free"
                                Events.AVAILABILITY_TENTATIVE -> "tentative"
                                else -> "busy"
                            },
                        )
                    )
                }
            }
        }.orEmpty()
    }

    override fun save(event: EventData): String = guarded {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, (event.calendarId ?: defaultCalendar().identifier).toLong())
            put(Events.TITLE, event.title)
            put(Events.EVENT_LOCATION, event.location)
            put(Events.DESCRIPTION, event.notes)
            put(Events.DTSTART, event.start)
            put(Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(Events.EVENT_TIMEZONE, if (event.allDay) "UTC" else event.timeZone ?: TimeZone.getDefault().id)
            put(Events.AVAILABILITY, when (event.availability) {
                "free" -> Events.AVAILABILITY_FREE
                "tentative" -> Events.AVAILABILITY_TENTATIVE
                else -> Events.AVAILABILITY_BUSY
            })
            if (event.rrule != null) {
                // Recurring events use RRULE + DURATION instead of DTEND.
                put(Events.RRULE, event.rrule)
                put(Events.DURATION, "P${(event.end - event.start).coerceAtLeast(0) / 1000}S")
                putNull(Events.DTEND)
            } else {
                putNull(Events.RRULE)
                putNull(Events.DURATION)
                put(Events.DTEND, event.end)
            }
        }
        val id = event.identifier
        if (id == null) {
            ContentUris.parseId(requireNotNull(resolver.insert(Events.CONTENT_URI, values)) { "Could not save the event" }).toString()
        } else {
            resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id.toLong()), values, null, null)
            id
        }
    }

    override fun remove(identifier: String) {
        guarded { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, identifier.toLong()), null, null) }
    }

    override fun openEditor(identifier: String?) {
        val intent = if (identifier == null) {
            Intent(Intent.ACTION_INSERT, Events.CONTENT_URI)
        } else {
            Intent(Intent.ACTION_EDIT, ContentUris.withAppendedId(Events.CONTENT_URI, identifier.toLong()))
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun Uri.asSyncAdapter(): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        throw IllegalStateException("Calendar permission not granted. Allow it in ScriptWidget → 권한.")
    }

    private companion object {
        const val LOCAL_ACCOUNT = "ScriptWidget"
    }
}
