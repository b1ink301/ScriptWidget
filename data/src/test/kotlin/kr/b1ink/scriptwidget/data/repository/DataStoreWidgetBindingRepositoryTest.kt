package kr.b1ink.scriptwidget.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStoreWidgetBindingRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun kotlinx.coroutines.test.TestScope.repo() = DataStoreWidgetBindingRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("bindings.preferences_pb").also { it.delete() } }
    )

    @Test
    fun `stores and reads bindings`() = runTest {
        val repo = repo()
        repo.set(WidgetBinding(1, "a", "p"))
        repo.set(WidgetBinding(2, "b", null))

        assertEquals(WidgetBinding(1, "a", "p"), repo.get(1))
        assertEquals(WidgetBinding(2, "b", null), repo.get(2))
        assertNull(repo.get(3))
    }

    @Test
    fun `overwriting with a null parameter clears it`() = runTest {
        val repo = repo()
        repo.set(WidgetBinding(1, "a", "p"))
        repo.set(WidgetBinding(1, "a", null))

        assertEquals(WidgetBinding(1, "a", null), repo.get(1))
    }

    @Test
    fun `remove forgets the binding`() = runTest {
        val repo = repo()
        repo.set(WidgetBinding(1, "a", "p"))
        repo.remove(1)

        assertNull(repo.get(1))
    }

    @Test
    fun `widgetsUsing returns sorted ids bound to the script`() = runTest {
        val repo = repo()
        repo.set(WidgetBinding(12, "a", null))
        repo.set(WidgetBinding(3, "a", null))
        repo.set(WidgetBinding(5, "b", null))

        assertEquals(listOf(3, 12), repo.widgetsUsing("a"))
        assertEquals(emptyList(), repo.widgetsUsing("zzz"))
    }
}
