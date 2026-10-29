package kr.b1ink.scriptwidget.ui.list

import kr.b1ink.scriptwidget.domain.model.ScriptAppearance

/** Scriptable's icon colors and a set of its (Font Awesome) glyph names, drawn as emoji. */
object ScriptIcons {
    val COLORS: Map<String, Long> = linkedMapOf(
        "red" to 0xFFFF453A, "pink" to 0xFFFF375F, "purple" to 0xFFBF5AF2, "deep-purple" to 0xFF7D3AC1,
        "deep-blue" to 0xFF3A47C9, "blue" to 0xFF0A84FF, "cyan" to 0xFF32ADE6, "teal" to 0xFF30B0A0,
        "green" to 0xFF30D158, "deep-green" to 0xFF248A3D, "yellow" to 0xFFFFD60A, "orange" to 0xFFFF9F0A,
        "brown" to 0xFFA2845E, "light-brown" to 0xFFC8A27A, "gray" to 0xFF8E8E93, "deep-gray" to 0xFF48484A,
        "light-gray" to 0xFFC7C7CC,
    )

    val GLYPHS: Map<String, String> = linkedMapOf(
        "magic" to "✨", "cloud" to "☁️", "sun" to "☀️", "moon" to "🌙", "umbrella" to "☂️", "bolt" to "⚡",
        "calendar" to "📅", "calendar-alt" to "📅", "clock" to "🕐", "bell" to "🔔", "tasks" to "✅", "star" to "⭐",
        "newspaper" to "📰", "book" to "📖", "chart-line" to "📈", "coins" to "🪙", "dollar-sign" to "💲", "shopping-cart" to "🛒",
        "home" to "🏠", "car" to "🚗", "bus" to "🚌", "train" to "🚆", "plane" to "✈️", "map-marker-alt" to "📍",
        "globe" to "🌍", "battery-full" to "🔋", "wifi" to "📶", "mobile-alt" to "📱", "code" to "💻", "terminal" to "⌨️",
        "music" to "🎵", "camera" to "📷", "heart" to "❤️", "fire" to "🔥", "leaf" to "🍃", "gamepad" to "🎮",
        "temperature-high" to "🌡️", "running" to "🏃", "utensils" to "🍴", "coffee" to "☕",
        "credit-card" to "💳", "wallet" to "👛", "gift" to "🎁", "graduation-cap" to "🎓", "film" to "🎬", "tv" to "📺",
        "rss" to "📡", "subway" to "🚇", "bicycle" to "🚲", "dumbbell" to "🏋️", "pills" to "💊", "paw" to "🐾",
        "sync" to "🔄", "list-ul" to "📋", "user" to "👤", "envelope" to "✉️", "image" to "🖼️", "lock" to "🔒",
        "key" to "🔑", "flag" to "🚩", "trophy" to "🏆", "lightbulb" to "💡", "snowflake" to "❄️", "tint" to "💧",
        "wind" to "🌬️", "cloud-sun" to "⛅", "cloud-rain" to "🌧️", "bolt-lightning" to "🌩️",
    )

    fun color(appearance: ScriptAppearance?): Long = COLORS[appearance?.color] ?: COLORS.getValue("gray")

    /** The glyph, or the script's first letter when there is none. */
    fun symbol(appearance: ScriptAppearance?, scriptName: String): String =
        appearance?.glyph?.let(GLYPHS::get)
            // "01_hello" → "H": names often start with a sort number.
            ?: (scriptName.firstOrNull { it.isLetter() } ?: scriptName.firstOrNull { it.isLetterOrDigit() })?.uppercase()
            ?: "?"
}
