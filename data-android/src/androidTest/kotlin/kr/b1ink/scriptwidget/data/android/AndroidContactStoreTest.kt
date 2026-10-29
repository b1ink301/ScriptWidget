package kr.b1ink.scriptwidget.data.android

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kr.b1ink.scriptwidget.data.engine.ContactChanges
import kr.b1ink.scriptwidget.data.engine.ContactData
import kr.b1ink.scriptwidget.data.engine.ContactGroup
import kr.b1ink.scriptwidget.data.engine.LabeledValue
import kr.b1ink.scriptwidget.data.engine.Membership
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidContactStoreTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    private val store = AndroidContactStore(ApplicationProvider.getApplicationContext<Context>())
    private val marker = "SWTest${System.nanoTime()}"

    @After
    fun tearDown() {
        val mine = store.contacts(null).filter { it.familyName == marker }.mapNotNull { it.identifier }
        val groups = store.groups(null).filter { it.name == marker }.mapNotNull { it.identifier }
        store.apply(ContactChanges(deletes = mine, groupDeletes = groups))
    }

    private fun mine() = store.contacts(null).filter { it.familyName == marker }

    @Test
    fun contactsAreAddedUpdatedAndDeleted() {
        store.apply(
            ContactChanges(
                adds = listOf(
                    ContactData(
                        containerId = store.defaultContainer().identifier, givenName = "Ada", familyName = marker,
                        organizationName = "Analytical", phoneNumbers = listOf(LabeledValue("mobile", "010-1234-5678")),
                        emailAddresses = listOf(LabeledValue("work", "ada@example.com")), note = "first programmer",
                    )
                )
            )
        )
        val added = mine().single()
        assertEquals("Ada", added.givenName)
        assertEquals("Analytical", added.organizationName)
        assertEquals("010-1234-5678", added.phoneNumbers.single().value)
        assertEquals("mobile", added.phoneNumbers.single().label)
        assertEquals("ada@example.com", added.emailAddresses.single().value)
        assertEquals("first programmer", added.note)

        store.apply(ContactChanges(updates = listOf(added.copy(givenName = "Augusta", phoneNumbers = listOf(LabeledValue("home", "02-000-0000"))))))
        val updated = mine().single()
        assertEquals("Augusta", updated.givenName)
        assertEquals(listOf("02-000-0000"), updated.phoneNumbers.map { it.value })

        store.apply(ContactChanges(deletes = listOf(updated.identifier!!)))
        assertTrue(mine().isEmpty())
    }

    @Test
    fun groupsAndMemberships() {
        val container = store.defaultContainer().identifier
        store.apply(ContactChanges(adds = listOf(ContactData(containerId = container, givenName = "G", familyName = marker)), groupAdds = listOf(ContactGroup(name = marker, containerId = container))))
        val contact = mine().single()
        val group = store.groups(null).single { it.name == marker }

        store.apply(ContactChanges(memberAdds = listOf(Membership(contact.identifier!!, group.identifier!!))))
        assertEquals(listOf(contact.identifier), store.contactsInGroups(listOf(group.identifier!!)).map { it.identifier })

        store.apply(ContactChanges(memberRemoves = listOf(Membership(contact.identifier!!, group.identifier!!))))
        assertTrue(store.contactsInGroups(listOf(group.identifier!!)).isEmpty())
    }
}
