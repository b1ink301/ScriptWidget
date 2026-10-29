package kr.b1ink.scriptwidget.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kr.b1ink.scriptwidget.domain.repository.WidgetBindingRepository

class DataStoreWidgetBindingRepository(
    private val dataStore: DataStore<Preferences>,
) : WidgetBindingRepository {

    override suspend fun get(appWidgetId: Int): WidgetBinding? {
        val prefs = dataStore.data.first()
        val script = prefs[scriptKey(appWidgetId)] ?: return null
        return WidgetBinding(appWidgetId, script, prefs[paramKey(appWidgetId)])
    }

    override suspend fun set(binding: WidgetBinding) {
        dataStore.edit {
            it[scriptKey(binding.appWidgetId)] = binding.scriptName
            val parameter = binding.parameter
            if (parameter == null) {
                it.remove(paramKey(binding.appWidgetId))
            } else {
                it[paramKey(binding.appWidgetId)] = parameter
            }
        }
    }

    override suspend fun remove(appWidgetId: Int) {
        dataStore.edit {
            it.remove(scriptKey(appWidgetId))
            it.remove(paramKey(appWidgetId))
        }
    }

    override suspend fun widgetsUsing(scriptName: String): List<Int> =
        dataStore.data.first().asMap()
            .filter { (key, value) -> key.name.startsWith(SCRIPT_PREFIX) && value == scriptName }
            .mapNotNull { (key, _) -> key.name.removePrefix(SCRIPT_PREFIX).toIntOrNull() }
            .sorted()

    private fun scriptKey(id: Int) = stringPreferencesKey("$SCRIPT_PREFIX$id")
    private fun paramKey(id: Int) = stringPreferencesKey("$PARAM_PREFIX$id")

    private companion object {
        const val SCRIPT_PREFIX = "script_"
        const val PARAM_PREFIX = "param_"
    }
}
