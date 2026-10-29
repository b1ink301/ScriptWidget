package kr.b1ink.scriptwidget.data.android

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import kr.b1ink.scriptwidget.data.engine.ContactChanges
import kr.b1ink.scriptwidget.data.engine.ContactContainer
import kr.b1ink.scriptwidget.data.engine.ContactData
import kr.b1ink.scriptwidget.data.engine.ContactGroup
import kr.b1ink.scriptwidget.data.engine.ContactStore
import kr.b1ink.scriptwidget.data.engine.LabeledValue
import kr.b1ink.scriptwidget.data.engine.PostalAddress
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * [ContactStore] over ContactsContract. Contact identifiers are raw contact ids; containers are
 * accounts ("local" = the phone itself).
 */
class AndroidContactStore(context: Context) : ContactStore {
    private val resolver = context.applicationContext.contentResolver

    private data class Account(val type: String?, val name: String?) {
        val id get() = if (type == null && name == null) LOCAL else "$type|$name"
    }

    private fun accountOf(containerId: String?): Account = when (containerId) {
        null, LOCAL -> Account(null, null)
        else -> containerId.split('|', limit = 2).let { Account(it[0], it.getOrNull(1)) }
    }

    override fun containers(): List<ContactContainer> = guarded {
        val accounts = resolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), null, null, null)
            ?.use { c -> buildSet { while (c.moveToNext()) add(Account(c.getString(0), c.getString(1))) } }.orEmpty()
        listOf(ContactContainer(LOCAL, "Phone")) + accounts.filter { it.id != LOCAL }.map { ContactContainer(it.id, it.name ?: it.type ?: "Account") }
    }

    override fun defaultContainer() = ContactContainer(LOCAL, "Phone")

    override fun contacts(containerIds: List<String>?): List<ContactData> = guarded {
        val raw = resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.DELETED} = 0", null, null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to Account(c.getString(1), c.getString(2)).id) } }.orEmpty()
            .filter { (_, container) -> containerIds == null || container in containerIds }
        load(raw)
    }

    override fun contactsInGroups(groupIds: List<String>): List<ContactData> = guarded {
        if (groupIds.isEmpty()) return@guarded emptyList()
        val ids = resolver.query(
            Data.CONTENT_URI, arrayOf(Data.RAW_CONTACT_ID),
            "${Data.MIMETYPE} = ? AND ${GroupMembership.GROUP_ROW_ID} IN (${groupIds.joinToString(",") { "?" }})",
            arrayOf(GroupMembership.CONTENT_ITEM_TYPE) + groupIds, null,
        )?.use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        contacts(null).filter { it.identifier?.toLong() in ids }
    }

    private fun load(raw: List<Pair<Long, String>>): List<ContactData> {
        if (raw.isEmpty()) return emptyList()
        val builders = raw.associate { (id, container) -> id to ContactBuilder(id, container) }
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10, Data.DATA15),
            "${Data.RAW_CONTACT_ID} IN (${builders.keys.joinToString(",")})", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val b = builders[c.getLong(0)] ?: continue
                val d = { i: Int -> c.getString(i + 1).orEmpty() }
                when (c.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE -> b.contact = b.contact.copy(givenName = d(2), familyName = d(3), namePrefix = d(4), middleName = d(5), nameSuffix = d(6))
                    Nickname.CONTENT_ITEM_TYPE -> b.contact = b.contact.copy(nickname = d(1))
                    Organization.CONTENT_ITEM_TYPE -> b.contact = b.contact.copy(organizationName = d(1), jobTitle = d(4), departmentName = d(5))
                    Note.CONTENT_ITEM_TYPE -> b.contact = b.contact.copy(note = d(1))
                    Phone.CONTENT_ITEM_TYPE -> b.phones += LabeledValue(label(phoneLabels, c.getInt(3), d(3)), d(1))
                    Email.CONTENT_ITEM_TYPE -> b.emails += LabeledValue(label(emailLabels, c.getInt(3), d(3)), d(1))
                    Website.CONTENT_ITEM_TYPE -> b.urls += LabeledValue(label(websiteLabels, c.getInt(3), d(3)), d(1))
                    StructuredPostal.CONTENT_ITEM_TYPE -> b.postal += PostalAddress(
                        label = label(postalLabels, c.getInt(3), d(3)), street = d(4), city = d(7), state = d(8), postalCode = d(9), country = d(10),
                    )
                    Event.CONTENT_ITEM_TYPE -> if (c.getInt(3) == Event.TYPE_BIRTHDAY) b.contact = b.contact.copy(birthday = parseBirthday(d(1)))
                    Photo.CONTENT_ITEM_TYPE -> if (!c.isNull(13)) b.contact = b.contact.copy(hasImage = true)
                }
            }
        }
        return builders.values.map { it.build() }
    }

    private class ContactBuilder(id: Long, container: String) {
        var contact = ContactData(identifier = id.toString(), containerId = container)
        val phones = mutableListOf<LabeledValue>()
        val emails = mutableListOf<LabeledValue>()
        val urls = mutableListOf<LabeledValue>()
        val postal = mutableListOf<PostalAddress>()
        fun build() = contact.copy(phoneNumbers = phones, emailAddresses = emails, urlAddresses = urls, postalAddresses = postal)
    }

    override fun image(contactId: String): ByteArray? = guarded {
        resolver.query(
            Data.CONTENT_URI, arrayOf(Photo.PHOTO), "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(contactId, Photo.CONTENT_ITEM_TYPE), null,
        )?.use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
    }

    override fun groups(containerIds: List<String>?): List<ContactGroup> = guarded {
        resolver.query(
            Groups.CONTENT_URI, arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_TYPE, Groups.ACCOUNT_NAME), "${Groups.DELETED} = 0", null, null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) add(ContactGroup(c.getLong(0).toString(), c.getString(1).orEmpty(), Account(c.getString(2), c.getString(3)).id))
            }
        }.orEmpty().filter { containerIds == null || it.containerId in containerIds }
    }

    override fun apply(changes: ContactChanges) = guarded {
        val ops = ArrayList<ContentProviderOperation>()
        changes.adds.forEach { contact ->
            val back = ops.size
            val account = accountOf(contact.containerId)
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .build()
            dataRows(contact).forEach { values -> ops += ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, back).withValues(values.toContentValues()).build() }
        }
        changes.updates.forEach { contact ->
            val id = requireNotNull(contact.identifier)
            // Replace the fields this API manages; photos and group memberships are kept.
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (${MANAGED.joinToString(",") { "?" }})",
                    arrayOf(id) + MANAGED,
                ).build()
            dataRows(contact).forEach { values ->
                ops += ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.RAW_CONTACT_ID, id.toLong()).withValues(values.toContentValues()).build()
            }
        }
        changes.deletes.forEach { ops += ContentProviderOperation.newDelete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, it.toLong())).build() }
        changes.groupAdds.forEach { group ->
            val account = accountOf(group.containerId)
            ops += ContentProviderOperation.newInsert(Groups.CONTENT_URI)
                .withValue(Groups.TITLE, group.name).withValue(Groups.GROUP_VISIBLE, 1)
                .withValue(Groups.ACCOUNT_TYPE, account.type).withValue(Groups.ACCOUNT_NAME, account.name).build()
        }
        changes.groupUpdates.forEach { group ->
            ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Groups.CONTENT_URI, requireNotNull(group.identifier).toLong()))
                .withValue(Groups.TITLE, group.name).build()
        }
        changes.groupDeletes.forEach { ops += ContentProviderOperation.newDelete(ContentUris.withAppendedId(Groups.CONTENT_URI, it.toLong())).build() }
        changes.memberAdds.forEach { m ->
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, m.contactId.toLong()).withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                .withValue(GroupMembership.GROUP_ROW_ID, m.groupId.toLong()).build()
        }
        changes.memberRemoves.forEach { m ->
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection("${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${GroupMembership.GROUP_ROW_ID} = ?", arrayOf(m.contactId, GroupMembership.CONTENT_ITEM_TYPE, m.groupId))
                .build()
        }
        if (ops.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        Unit
    }

    private fun dataRows(c: ContactData): List<Map<String, Any?>> = buildList {
        add(mapOf(Data.MIMETYPE to StructuredName.CONTENT_ITEM_TYPE, StructuredName.GIVEN_NAME to c.givenName, StructuredName.FAMILY_NAME to c.familyName,
            StructuredName.PREFIX to c.namePrefix, StructuredName.MIDDLE_NAME to c.middleName, StructuredName.SUFFIX to c.nameSuffix))
        if (c.nickname.isNotEmpty()) add(mapOf(Data.MIMETYPE to Nickname.CONTENT_ITEM_TYPE, Nickname.NAME to c.nickname))
        if (c.organizationName.isNotEmpty() || c.jobTitle.isNotEmpty() || c.departmentName.isNotEmpty()) {
            add(mapOf(Data.MIMETYPE to Organization.CONTENT_ITEM_TYPE, Organization.COMPANY to c.organizationName, Organization.TITLE to c.jobTitle, Organization.DEPARTMENT to c.departmentName))
        }
        if (c.note.isNotEmpty()) add(mapOf(Data.MIMETYPE to Note.CONTENT_ITEM_TYPE, Note.NOTE to c.note))
        c.birthday?.let { add(mapOf(Data.MIMETYPE to Event.CONTENT_ITEM_TYPE, Event.TYPE to Event.TYPE_BIRTHDAY, Event.START_DATE to LocalDate.ofEpochDay(it / 86_400_000L).toString())) }
        c.phoneNumbers.forEach { add(labeledRow(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER, phoneLabels, it)) }
        c.emailAddresses.forEach { add(labeledRow(Email.CONTENT_ITEM_TYPE, Email.ADDRESS, emailLabels, it)) }
        c.urlAddresses.forEach { add(labeledRow(Website.CONTENT_ITEM_TYPE, Website.URL, websiteLabels, it)) }
        c.postalAddresses.forEach { a ->
            val (type, custom) = typeOf(postalLabels, a.label)
            add(mapOf(Data.MIMETYPE to StructuredPostal.CONTENT_ITEM_TYPE, StructuredPostal.TYPE to type, StructuredPostal.LABEL to custom,
                StructuredPostal.STREET to a.street, StructuredPostal.CITY to a.city, StructuredPostal.REGION to a.state,
                StructuredPostal.POSTCODE to a.postalCode, StructuredPostal.COUNTRY to a.country))
        }
    }

    private fun Map<String, Any?>.toContentValues() = android.content.ContentValues().also { cv ->
        forEach { (key, value) ->
            when (value) {
                null -> cv.putNull(key)
                is Int -> cv.put(key, value)
                is Long -> cv.put(key, value)
                else -> cv.put(key, value.toString())
            }
        }
    }

    private fun labeledRow(mime: String, valueColumn: String, labels: Map<Int, String>, v: LabeledValue): Map<String, Any?> {
        val (type, custom) = typeOf(labels, v.label)
        return mapOf(Data.MIMETYPE to mime, valueColumn to v.value, Data.DATA2 to type, Data.DATA3 to custom)
    }

    private fun typeOf(labels: Map<Int, String>, label: String?): Pair<Int, String?> =
        labels.entries.firstOrNull { it.value.equals(label, ignoreCase = true) }?.let { it.key to null }
            ?: (if (label.isNullOrEmpty()) labels.keys.first() to null else 0 to label)

    private fun label(labels: Map<Int, String>, type: Int, custom: String): String? =
        if (type == 0) custom.takeIf { it.isNotEmpty() } else labels[type]

    private fun parseBirthday(value: String): Long? = runCatching {
        LocalDate.parse(value.replace("--", "1604-")).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        throw IllegalStateException("Contacts permission not granted. Allow it in ScriptWidget → 권한.")
    }

    private companion object {
        const val LOCAL = "local"
        val phoneLabels = mapOf(Phone.TYPE_MOBILE to "mobile", Phone.TYPE_HOME to "home", Phone.TYPE_WORK to "work", Phone.TYPE_MAIN to "main", Phone.TYPE_OTHER to "other")
        val emailLabels = mapOf(Email.TYPE_HOME to "home", Email.TYPE_WORK to "work", Email.TYPE_OTHER to "other")
        val websiteLabels = mapOf(Website.TYPE_HOMEPAGE to "homepage", Website.TYPE_HOME to "home", Website.TYPE_WORK to "work", Website.TYPE_OTHER to "other")
        val postalLabels = mapOf(StructuredPostal.TYPE_HOME to "home", StructuredPostal.TYPE_WORK to "work", StructuredPostal.TYPE_OTHER to "other")
        val MANAGED = arrayOf(
            StructuredName.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE,
            Event.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE,
        )
    }
}
