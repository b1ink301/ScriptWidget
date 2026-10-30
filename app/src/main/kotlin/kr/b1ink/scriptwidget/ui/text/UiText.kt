package kr.b1ink.scriptwidget.ui.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text a ViewModel hands to the screen: a string resource (translated where it is shown) or text
 * that comes from elsewhere (a file name, an error message from a script).
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val text: String) : UiText

    /** A quantity string; [count] picks the form and is also the first format argument. */
    data class Plural(@PluralsRes val id: Int, val count: Int) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
        is Raw -> text
        is Plural -> context.resources.getQuantityString(id, count, count)
    }

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int): UiText = Plural(id, count)
    }
}

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
