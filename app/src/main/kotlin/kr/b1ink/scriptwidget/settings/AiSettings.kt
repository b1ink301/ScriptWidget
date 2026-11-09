package kr.b1ink.scriptwidget.settings

import android.content.SharedPreferences
import kr.b1ink.scriptwidget.data.ai.AiProvider
import kr.b1ink.scriptwidget.data.engine.SecretStore

/**
 * "Make a widget with AI": the chosen provider and model per provider, and the user's API keys.
 * Keys live in their own Keystore-encrypted store, not the scripts' Keychain, so scripts can't read them.
 */
class AiSettings(private val prefs: SharedPreferences, private val keys: SecretStore) {
    var provider: AiProvider
        get() = prefs.getString(PROVIDER, null)?.let { name -> AiProvider.entries.firstOrNull { it.name == name } } ?: AiProvider.Gemini
        set(value) = prefs.edit().putString(PROVIDER, value.name).apply()

    fun model(provider: AiProvider): String = prefs.getString("$MODEL.${provider.name}", null).orEmpty()

    fun setModel(provider: AiProvider, model: String) = prefs.edit().putString("$MODEL.${provider.name}", model.trim()).apply()

    fun key(provider: AiProvider): String? = runCatching { keys.get(provider.name) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun setKey(provider: AiProvider, key: String?) {
        if (key.isNullOrBlank()) keys.remove(provider.name) else keys.set(provider.name, key.trim())
    }

    private companion object {
        const val PROVIDER = "provider"
        const val MODEL = "model"
    }
}
