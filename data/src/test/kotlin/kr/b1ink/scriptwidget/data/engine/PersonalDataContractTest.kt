package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.data.personal.FileReminderStore
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Calendar, CalendarEvent, RecurrenceRule, Reminder, Contact(s), ContactsGroup and Photos. */
class PersonalDataContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val calendar = FakeCalendarStore()
    private val contacts = FakeContactStore()
    private val photos = FakePhotoLibrary()
    private val notifier = FakeNotifier()
    private val ui = FakeUiPresenter()
    private val images = FakeImageStore()
    private val reminders by lazy { FileReminderStore(tmp.root.resolve("reminders.json"), notifier) }

    private suspend fun run(script: String, inWidget: Boolean = false) = QuickJsScriptEngine(
        Prelude.load(),
        fakeServices(
            images = images, files = SandboxedFileSystem(tmp.newFolder()), ui = ui, calendar = calendar,
            reminders = reminders, contacts = contacts, photos = photos,
        ),
        timeoutMillis = 3_000,
    ).execute(script, ScriptEnvironment(runsInWidget = inWidget), "personal")

    private suspend fun out(script: String, inWidget: Boolean = false): String {
        val result = run(script, inWidget)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    // --- Calendar & CalendarEvent ---

    @Test
    fun `calendars are listed and found by title`() = runTest {
        assertEquals(
            "Personal,Work true Work #FF5722 Personal",
            out(
                """
                const all = await Calendar.forEvents();
                const work = await Calendar.forEventsByTitle("Work");
                const def = await Calendar.defaultForEvents();
                console.log(all.map(c => c.title).join(","), work.allowsContentModifications, work.title, "#" + work.color.hex, def.title);
                """
            ),
        )
    }

    @Test
    fun `today queries one local day and maps events`() = runTest {
        val now = System.currentTimeMillis()
        calendar.events += EventData("e1", "2", "Standup", "Room 1", "notes", now, now + 1_800_000, rrule = "FREQ=DAILY")
        val log = out(
            """
            const events = await CalendarEvent.today([]);
            const e = events[0];
            console.log(events.length, e.title, e.location, e.calendar.title, e.startDate instanceof Date, e.isAllDay);
            """
        )
        assertEquals("1 Standup Room 1 Work true false", log)
        val (start, end, ids) = calendar.queries.single()
        assertEquals(86_400_000L, end - start)
        assertTrue(now in start until end)
        assertNull(ids)
    }

    @Test
    fun `week and range helpers pass the calendars to filter`() = runTest {
        out(
            """
            const [work] = await Promise.all([Calendar.forEventsByTitle("Work")]);
            await CalendarEvent.thisWeek([work]);
            await CalendarEvent.between(new Date(1000), new Date(5000), [work]);
            """
        )
        val (weekStart, weekEnd, ids) = calendar.queries[0]
        assertEquals(7 * 86_400_000L, weekEnd - weekStart)
        assertEquals(listOf("2"), ids)
        assertEquals(Triple(1000L, 5000L, listOf("2")), calendar.queries[1])
    }

    @Test
    fun `events are created, updated with recurrence and removed`() = runTest {
        out(
            """
            const e = new CalendarEvent();
            e.title = "Gym";
            e.startDate = new Date(1700000000000);
            e.endDate = new Date(1700003600000);
            e.isAllDay = false;
            e.addRecurrenceRule(RecurrenceRule.complexWeeklyOccurrenceCount(1, [2, 4], [], 10));
            await e.save();
            console.log(e.identifier);
            e.title = "Gym!";
            await e.save();
            """
        )
        val saved = calendar.events.single()
        assertEquals("Gym!", saved.title)
        assertEquals("1", saved.calendarId)
        assertEquals("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=10", saved.rrule)

        out("""const [e] = await CalendarEvent.between(new Date(0), new Date(2000000000000)); e.remove(); """)
        assertTrue(calendar.events.isEmpty())
    }

    @Test
    fun `recurrence rules build RRULEs`() = runTest {
        assertEquals(
            listOf(
                "FREQ=DAILY;INTERVAL=1",
                "FREQ=WEEKLY;INTERVAL=2;COUNT=3",
                "FREQ=MONTHLY;INTERVAL=1;UNTIL=20231114T221320Z",
                "FREQ=YEARLY;INTERVAL=1",
                "FREQ=MONTHLY;INTERVAL=1;BYDAY=FR;BYMONTHDAY=13",
            ).joinToString("\n"),
            out(
                """
                console.log(RecurrenceRule.daily(1)._rrule);
                console.log(RecurrenceRule.weeklyOccurrenceCount(2, 3)._rrule);
                console.log(RecurrenceRule.monthlyEndDate(1, new Date(1700000000000))._rrule);
                console.log(RecurrenceRule.yearly(1)._rrule);
                console.log(RecurrenceRule.complexMonthly(1, [6], [13], [])._rrule);
                """
            ),
        )
    }

    @Test
    fun `presentEdit opens the system calendar from the app only`() = runTest {
        out("""const e = new CalendarEvent(); await e.presentEdit(); await CalendarEvent.presentCreate();""")
        assertEquals(listOf<String?>(null, null), calendar.edited)
        assertContains(assertNotNull(run("""await CalendarEvent.presentCreate();""", inWidget = true).error), "widget")
    }

    @Test
    fun `calendar events are readable during widget refreshes`() = runTest {
        out("""await CalendarEvent.today(); Script.setWidget(new ListWidget());""", inWidget = true)
        assertEquals(1, calendar.queries.size)
    }

    // --- Reminder (app-local store) ---

    @Test
    fun `reminder lists and reminders round trip`() = runTest {
        val log = out(
            """
            const list = await Calendar.findOrCreateForReminders("Groceries");
            const again = await Calendar.findOrCreateForReminders("Groceries");
            const r = new Reminder();
            r.title = "Milk";
            r.calendar = list;
            r.dueDate = new Date(Date.now() + 3600000);
            r.priority = 1;
            r.save();
            const done = new Reminder();
            done.title = "Bread"; done.calendar = list; done.isCompleted = true; done.save();
            const lists = await Calendar.forReminders();
            const all = await Reminder.all([list]);
            const open = await Reminder.allIncomplete([list]);
            const scheduled = await Reminder.scheduled([list]);
            console.log(list.identifier === again.identifier, lists.some(l => l.title === "Groceries"));
            console.log(all.length, open.map(x => x.title).join(","), scheduled.length, all.find(x => x.title === "Bread").completionDate instanceof Date);
            """
        )
        assertEquals("true true\n2 Milk 1 true", log)
        assertEquals("Milk", notifier.scheduled.single().title)
    }

    @Test
    fun `reminders due today and removal`() = runTest {
        val log = out(
            """
            const r = new Reminder(); r.title = "Today"; r.dueDate = new Date(); r.save();
            const later = new Reminder(); later.title = "Later"; later.dueDate = new Date(Date.now() + 5 * 86400000); later.save();
            const today = await Reminder.allDueToday();
            console.log(today.map(x => x.title).join(","));
            r.remove();
            console.log((await Reminder.all()).map(x => x.title).join(","));
            """
        )
        assertEquals("Today\nLater", log)
    }

    // --- Contacts ---

    @Test
    fun `contacts are listed with their labeled values and images`() = runTest {
        contacts.contacts += ContactData(
            identifier = "with-photo", containerId = "local", givenName = "Ada", familyName = "Lovelace",
            phoneNumbers = listOf(LabeledValue("mobile", "010-1234-5678")), emailAddresses = listOf(LabeledValue("work", "ada@example.com")),
            hasImage = true,
        )
        val log = out(
            """
            const [c] = await Contact.all([await ContactsContainer.default()]);
            console.log(c.givenName, c.familyName, c.phoneNumbers[0].value, c.phoneNumbers[0].label, c.emailAddresses[0].value, c.isImageAvailable, c.image instanceof Image);
            """
        )
        assertEquals("Ada Lovelace 010-1234-5678 mobile ada@example.com true true", log)
    }

    @Test
    fun `contact changes are applied together on persistChanges`() = runTest {
        contacts.contacts += ContactData(identifier = "x", containerId = "local", givenName = "Old")
        out(
            """
            const c = new Contact();
            c.givenName = "Grace";
            c.familyName = "Hopper";
            c.phoneNumbers = [{ label: "mobile", value: "010-0000-0000" }];
            Contact.add(c);
            const [old] = await Contact.all([await ContactsContainer.default()]);
            old.givenName = "New";
            Contact.update(old);
            await Contact.persistChanges();
            """
        )
        val change = contacts.applied.single()
        assertEquals("Grace", change.adds.single().givenName)
        assertEquals("local", change.adds.single().containerId)
        assertEquals("New", change.updates.single().givenName)
        assertEquals(listOf("Grace", "New").sorted(), contacts.contacts.map { it.givenName }.sorted())
    }

    @Test
    fun `groups and memberships`() = runTest {
        contacts.contacts += ContactData(identifier = "a", containerId = "local", givenName = "A")
        contacts.groups += ContactGroup("grp", "Friends", "local")
        val log = out(
            """
            const [g] = await ContactsGroup.all([await ContactsContainer.default()]);
            const [a] = await Contact.all();
            g.addMember(a);
            await Contact.persistChanges();
            console.log(g.name, (await Contact.inGroups([g])).map(c => c.givenName).join(","));
            const containers = await ContactsContainer.all();
            console.log(containers.map(c => c.name).join(","), (await ContactsContainer.withIdentifier("g")).name);
            """
        )
        assertEquals("Friends A\nPhone,Google Google", log)
    }

    // --- Photos ---

    @Test
    fun `latest photos and screenshots become Images`() = runTest {
        val log = out(
            """
            const [a, b] = await Photos.latestPhotos(2);
            const s = await Photos.latestScreenshot();
            console.log(a instanceof Image, b instanceof Image, s instanceof Image, (await Photos.latestPhoto()) instanceof Image);
            """
        )
        assertEquals("true true true true", log)
        assertEquals(listOf(1, 1, 1, 1), images.stored.map { it.size })
    }

    @Test
    fun `library and camera use the system pickers in the app`() = runTest {
        assertEquals("true", out("""console.log((await Photos.fromLibrary()) instanceof Image);"""))
        out("""await Photos.fromCamera();""")
        assertEquals(listOf("library", "camera"), ui.imagePicks)
        assertContains(assertNotNull(run("""await Photos.fromLibrary();""", inWidget = true).error), "widget")
        ui.imageAnswer = null
        assertContains(assertNotNull(run("""await Photos.fromLibrary();""").error), "cancel")
    }

    @Test
    fun `images can be saved to the photo library`() = runTest {
        out("""Photos.save(await new Request("https://img").loadImage());""")
        assertContentEquals(FakeImageStore.PNG_BYTES, photos.saved.single())
    }
}
