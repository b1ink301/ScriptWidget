package kr.b1ink.scriptwidget.data.engine

import kotlinx.serialization.Serializable

/** A calendar for events, or a reminder list. */
@Serializable
data class CalendarInfo(
    val identifier: String,
    val title: String,
    /** `#AARRGGBB` */
    val color: String? = null,
    val allowsContentModifications: Boolean = true,
    val isSubscribed: Boolean = false,
)

@Serializable
data class Attendee(val name: String? = null, val status: String? = null, val type: String? = null, val role: String? = null)

@Serializable
data class EventData(
    val identifier: String? = null,
    val calendarId: String? = null,
    val title: String = "",
    val location: String? = null,
    val notes: String? = null,
    /** Epoch millis. */
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
    val timeZone: String? = null,
    val rrule: String? = null,
    val availability: String? = null,
    val attendees: List<Attendee> = emptyList(),
)

/** Device calendars (CalendarContract on Android). */
interface CalendarStore {
    fun calendars(): List<CalendarInfo>

    /** The calendar new events go to; implementations may create a local one when none exists. */
    fun defaultCalendar(): CalendarInfo

    /** Event occurrences overlapping [start, end) (recurring events expanded). */
    fun events(start: Long, end: Long, calendarIds: List<String>?): List<EventData>

    /** Inserts or updates; returns the identifier. */
    fun save(event: EventData): String
    fun remove(identifier: String)

    /** Opens the system calendar to edit an event, or to create one when [identifier] is null. */
    fun openEditor(identifier: String?)
}

@Serializable
data class ReminderData(
    val identifier: String? = null,
    val listId: String? = null,
    val title: String = "",
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val completionDate: Long? = null,
    val creationDate: Long? = null,
    val dueDate: Long? = null,
    val dueDateIncludesTime: Boolean = true,
    val priority: Int = 0,
    val rrule: String? = null,
)

/** Reminders. Android has no system reminders app, so they are stored by the app itself. */
interface ReminderStore {
    fun lists(): List<CalendarInfo>
    fun defaultList(): CalendarInfo
    fun createList(title: String): CalendarInfo
    fun renameList(identifier: String, title: String)
    fun removeList(identifier: String)
    fun reminders(listIds: List<String>?): List<ReminderData>

    /** Inserts or updates; returns the identifier. */
    fun save(reminder: ReminderData): String
    fun remove(identifier: String)
}

@Serializable
data class LabeledValue(val label: String? = null, val value: String, val identifier: String? = null)

@Serializable
data class PostalAddress(
    val label: String? = null,
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "",
    val identifier: String? = null,
)

@Serializable
data class ContactData(
    val identifier: String? = null,
    val containerId: String? = null,
    val namePrefix: String = "",
    val givenName: String = "",
    val middleName: String = "",
    val familyName: String = "",
    val nameSuffix: String = "",
    val nickname: String = "",
    val organizationName: String = "",
    val departmentName: String = "",
    val jobTitle: String = "",
    val note: String = "",
    /** Epoch millis (date only). */
    val birthday: Long? = null,
    val phoneNumbers: List<LabeledValue> = emptyList(),
    val emailAddresses: List<LabeledValue> = emptyList(),
    val urlAddresses: List<LabeledValue> = emptyList(),
    val postalAddresses: List<PostalAddress> = emptyList(),
    val hasImage: Boolean = false,
)

@Serializable
data class ContactContainer(val identifier: String, val name: String)

@Serializable
data class ContactGroup(val identifier: String? = null, val name: String, val containerId: String? = null)

@Serializable
data class Membership(val contactId: String, val groupId: String)

/** Everything queued with Contact/ContactsGroup add/update/delete, applied by `Contact.persistChanges()`. */
@Serializable
data class ContactChanges(
    val adds: List<ContactData> = emptyList(),
    val updates: List<ContactData> = emptyList(),
    val deletes: List<String> = emptyList(),
    val groupAdds: List<ContactGroup> = emptyList(),
    val groupUpdates: List<ContactGroup> = emptyList(),
    val groupDeletes: List<String> = emptyList(),
    val memberAdds: List<Membership> = emptyList(),
    val memberRemoves: List<Membership> = emptyList(),
)

/** Device contacts (ContactsContract on Android); containers are accounts. */
interface ContactStore {
    fun containers(): List<ContactContainer>
    fun defaultContainer(): ContactContainer
    fun contacts(containerIds: List<String>?): List<ContactData>
    fun contactsInGroups(groupIds: List<String>): List<ContactData>
    fun image(contactId: String): ByteArray?
    fun groups(containerIds: List<String>?): List<ContactGroup>
    fun apply(changes: ContactChanges)
}

/** The device photo library (MediaStore on Android). */
interface PhotoLibrary {
    /** Newest first. */
    fun latest(count: Int, screenshots: Boolean): List<ByteArray>

    /** Bytes of a picked photo (handle from the picker). */
    fun read(handle: String): ByteArray?
    fun save(bytes: ByteArray)
}

private fun unavailable(what: String): Nothing = throw IllegalStateException("$what is not available")

object NoCalendar : CalendarStore {
    override fun calendars() = unavailable("Calendar")
    override fun defaultCalendar() = unavailable("Calendar")
    override fun events(start: Long, end: Long, calendarIds: List<String>?) = unavailable("Calendar")
    override fun save(event: EventData) = unavailable("Calendar")
    override fun remove(identifier: String) = unavailable("Calendar")
    override fun openEditor(identifier: String?) = unavailable("Calendar")
}

object NoContacts : ContactStore {
    override fun containers() = unavailable("Contacts")
    override fun defaultContainer() = unavailable("Contacts")
    override fun contacts(containerIds: List<String>?) = unavailable("Contacts")
    override fun contactsInGroups(groupIds: List<String>) = unavailable("Contacts")
    override fun image(contactId: String) = unavailable("Contacts")
    override fun groups(containerIds: List<String>?) = unavailable("Contacts")
    override fun apply(changes: ContactChanges) = unavailable("Contacts")
}

object NoPhotos : PhotoLibrary {
    override fun latest(count: Int, screenshots: Boolean) = unavailable("Photos")
    override fun read(handle: String) = unavailable("Photos")
    override fun save(bytes: ByteArray) = unavailable("Photos")
}
