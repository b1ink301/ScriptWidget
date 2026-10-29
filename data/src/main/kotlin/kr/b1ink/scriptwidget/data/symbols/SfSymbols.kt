package kr.b1ink.scriptwidget.data.symbols

/**
 * Maps Apple SF Symbol names to Material Icons (Android has no SF Symbols).
 *
 * Lookup: exact name → without `.fill` → without shape suffixes (`.circle`, `.square`, …)
 * → shorter prefixes → a Material icon name used directly → [FALLBACK].
 */
object SfSymbols {
    const val FALLBACK = "help_outline"

    data class Resolved(val materialName: String, val isFallback: Boolean)

    private val table: Map<String, String> = mapOf(
        // weather
        "sun.max" to "wb_sunny", "sun.min" to "light_mode", "sunrise" to "wb_twilight", "sunset" to "wb_twilight",
        "sun.haze" to "wb_sunny", "sun.dust" to "wb_sunny", "moon" to "dark_mode", "moon.stars" to "nights_stay",
        "moon.zzz" to "bedtime", "cloud" to "cloud", "cloud.sun" to "wb_cloudy", "cloud.moon" to "nights_stay",
        "cloud.rain" to "umbrella", "cloud.heavyrain" to "thunderstorm", "cloud.drizzle" to "grain",
        "cloud.sun.rain" to "umbrella", "cloud.moon.rain" to "umbrella", "cloud.bolt" to "thunderstorm",
        "cloud.bolt.rain" to "thunderstorm", "cloud.snow" to "ac_unit", "cloud.sleet" to "ac_unit",
        "cloud.hail" to "ac_unit", "cloud.fog" to "foggy", "smoke" to "foggy", "snowflake" to "ac_unit",
        "snow" to "ac_unit", "wind" to "air", "wind.snow" to "air", "tornado" to "tornado", "hurricane" to "cyclone",
        "thermometer" to "thermostat", "thermometer.sun" to "thermostat", "thermometer.snowflake" to "thermostat",
        "drop" to "water_drop", "humidity" to "water_drop", "umbrella" to "umbrella", "aqi.low" to "air",
        "aqi.medium" to "air", "aqi.high" to "air", "rainbow" to "looks", "sparkles" to "auto_awesome",
        // arrows & navigation
        "arrow.up" to "arrow_upward", "arrow.down" to "arrow_downward", "arrow.left" to "arrow_back",
        "arrow.right" to "arrow_forward", "arrow.up.right" to "north_east", "arrow.up.left" to "north_west",
        "arrow.down.right" to "south_east", "arrow.down.left" to "south_west", "arrow.up.arrow.down" to "swap_vert",
        "arrow.left.arrow.right" to "swap_horiz", "arrow.clockwise" to "refresh", "arrow.counterclockwise" to "replay",
        "arrow.triangle.2.circlepath" to "sync", "arrow.2.circlepath" to "autorenew", "arrow.uturn.left" to "undo",
        "arrow.uturn.right" to "redo", "chevron.up" to "expand_less", "chevron.down" to "expand_more",
        "chevron.left" to "chevron_left", "chevron.right" to "chevron_right", "arrowtriangle.up" to "arrow_drop_up",
        "arrowtriangle.down" to "arrow_drop_down", "arrowtriangle.left" to "arrow_left",
        "arrowtriangle.right" to "arrow_right", "arrow.up.right.square" to "open_in_new",
        "chart.line.uptrend.xyaxis" to "trending_up", "chart.line.downtrend.xyaxis" to "trending_down",
        "location.north" to "navigation", "safari" to "explore",
        // status & symbols
        "checkmark" to "check", "checkmark.circle" to "check_circle", "checkmark.seal" to "verified",
        "checkmark.shield" to "verified_user", "xmark" to "close", "xmark.circle" to "cancel", "xmark.octagon" to "dangerous",
        "plus" to "add", "plus.circle" to "add_circle", "minus" to "remove", "minus.circle" to "remove_circle",
        "exclamationmark.triangle" to "warning", "exclamationmark.circle" to "error",
        "exclamationmark" to "priority_high", "info.circle" to "info", "info" to "info",
        "questionmark.circle" to "help", "questionmark" to "question_mark", "star" to "star",
        "star.leadinghalf.filled" to "star_half", "heart" to "favorite", "heart.slash" to "heart_broken",
        "bell" to "notifications", "bell.slash" to "notifications_off", "bell.badge" to "notification_important",
        "flag" to "flag", "bookmark" to "bookmark", "tag" to "sell", "pin" to "push_pin", "bolt" to "bolt",
        "bolt.slash" to "flash_off", "flame" to "local_fire_department", "leaf" to "eco", "pawprint" to "pets",
        "hand.thumbsup" to "thumb_up", "hand.thumbsdown" to "thumb_down", "hand.raised" to "back_hand",
        "eye" to "visibility", "eye.slash" to "visibility_off", "lightbulb" to "lightbulb", "power" to "power_settings_new",
        "circle" to "circle", "square" to "crop_square", "triangle" to "change_history", "seal" to "verified",
        "ellipsis" to "more_horiz", "ellipsis.circle" to "more_horiz", "line.3.horizontal" to "menu",
        "list.bullet" to "list", "list.number" to "format_list_numbered", "square.grid.2x2" to "grid_view",
        "square.grid.3x3" to "apps", "square.grid.4x3" to "apps", "rectangle.grid.2x2" to "grid_view",
        "slider.horizontal.3" to "tune", "gear" to "settings", "gearshape" to "settings", "gearshape.2" to "settings",
        "wrench" to "build", "wrench.and.screwdriver" to "handyman", "hammer" to "construction",
        "magnifyingglass" to "search", "trash" to "delete", "pencil" to "edit", "square.and.pencil" to "edit_note",
        "square.and.arrow.up" to "ios_share", "square.and.arrow.down" to "download", "link" to "link",
        "paperclip" to "attach_file", "lock" to "lock", "lock.open" to "lock_open", "key" to "key",
        "shield" to "shield", "percent" to "percent", "number" to "tag", "textformat" to "text_fields",
        // devices & connectivity
        "battery.100" to "battery_full", "battery.75" to "battery_5_bar", "battery.50" to "battery_4_bar",
        "battery.25" to "battery_2_bar", "battery.0" to "battery_alert", "battery.100.bolt" to "battery_charging_full",
        "bolt.batteryblock" to "battery_charging_full", "wifi" to "wifi", "wifi.slash" to "wifi_off",
        "antenna.radiowaves.left.and.right" to "cell_tower", "dot.radiowaves.left.and.right" to "sensors",
        "cellularbars" to "signal_cellular_alt", "airplane" to "flight", "iphone" to "smartphone",
        "ipad" to "tablet", "applewatch" to "watch", "laptopcomputer" to "laptop", "desktopcomputer" to "desktop_windows",
        "display" to "monitor", "tv" to "tv", "server.rack" to "dns", "externaldrive" to "storage",
        "internaldrive" to "storage", "cpu" to "memory", "memorychip" to "memory", "printer" to "print",
        "keyboard" to "keyboard", "headphones" to "headphones", "airpods" to "headphones",
        "speaker" to "volume_mute", "speaker.wave.1" to "volume_down", "speaker.wave.2" to "volume_up",
        "speaker.wave.3" to "volume_up", "speaker.slash" to "volume_off", "mic" to "mic", "mic.slash" to "mic_off",
        "camera" to "photo_camera", "video" to "videocam", "photo" to "image", "photo.on.rectangle" to "collections",
        "gamecontroller" to "sports_esports", "network" to "lan", "globe" to "public",
        "icloud" to "cloud_queue", "icloud.and.arrow.down" to "cloud_download", "icloud.and.arrow.up" to "cloud_upload",
        "externaldrive.connected.to.line.below" to "lan",
        // places, travel, money
        "house" to "home", "building" to "business", "building.2" to "apartment", "building.columns" to "account_balance",
        "car" to "directions_car", "car.2" to "directions_car", "bolt.car" to "electric_car", "bus" to "directions_bus",
        "tram" to "tram", "train.side.front.car" to "train", "bicycle" to "pedal_bike", "scooter" to "electric_scooter",
        "figure.walk" to "directions_walk", "figure.run" to "directions_run", "figure.wave" to "emoji_people",
        "ferry" to "directions_boat", "fuelpump" to "local_gas_station", "ev.charger" to "ev_station",
        "parkingsign" to "local_parking", "location" to "near_me", "location.fill" to "near_me",
        "mappin" to "place", "mappin.and.ellipse" to "place", "map" to "map", "signpost.right" to "signpost",
        "cart" to "shopping_cart", "bag" to "shopping_bag", "basket" to "shopping_basket", "creditcard" to "credit_card",
        "banknote" to "payments", "dollarsign" to "attach_money", "dollarsign.circle" to "monetization_on",
        "eurosign" to "euro", "eurosign.circle" to "euro", "yensign" to "currency_yen", "sterlingsign" to "currency_pound",
        "wonsign" to "currency_exchange", "bitcoinsign" to "currency_bitcoin", "bitcoinsign.circle" to "currency_bitcoin",
        "chart.bar" to "bar_chart", "chart.pie" to "pie_chart", "chart.xyaxis.line" to "show_chart", "gift" to "card_giftcard",
        "shippingbox" to "inventory_2", "cube" to "view_in_ar", "cube.box" to "inventory_2",
        // people, communication, time
        "person" to "person", "person.circle" to "account_circle", "person.crop.circle" to "account_circle",
        "person.2" to "group", "person.3" to "groups", "person.badge.plus" to "person_add",
        "envelope" to "mail", "envelope.open" to "drafts", "paperplane" to "send", "phone" to "call",
        "phone.down" to "call_end", "message" to "chat", "bubble.left" to "chat_bubble", "bubble.right" to "chat_bubble",
        "bubble.left.and.bubble.right" to "forum", "quote.bubble" to "format_quote", "at" to "alternate_email",
        "calendar" to "calendar_today", "calendar.badge.plus" to "event", "calendar.badge.clock" to "event",
        "clock" to "schedule", "alarm" to "alarm", "timer" to "timer", "stopwatch" to "timer", "hourglass" to "hourglass_empty",
        "deskclock" to "schedule", "birthday.cake" to "cake",
        // media & documents
        "play" to "play_arrow", "pause" to "pause", "stop" to "stop", "forward" to "fast_forward",
        "backward" to "fast_rewind", "forward.end" to "skip_next", "backward.end" to "skip_previous",
        "shuffle" to "shuffle", "repeat" to "repeat", "music.note" to "music_note", "music.note.list" to "queue_music",
        "music.mic" to "mic_external_on", "waveform" to "graphic_eq", "film" to "movie", "book" to "menu_book",
        "books.vertical" to "library_books", "newspaper" to "newspaper", "doc" to "description",
        "doc.text" to "article", "doc.on.doc" to "content_copy", "folder" to "folder", "tray" to "inbox",
        "archivebox" to "archive", "note.text" to "sticky_note_2", "list.clipboard" to "assignment",
        "graduationcap" to "school", "pencil.and.outline" to "draw", "paintbrush" to "brush", "paintpalette" to "palette",
        "photo.artframe" to "wallpaper",
        // health, food, sport
        "heart.text.square" to "monitor_heart", "waveform.path.ecg" to "monitor_heart", "cross" to "add",
        "cross.case" to "medical_services", "pills" to "medication", "bed.double" to "bed", "lungs" to "air",
        "figure.walk.circle" to "directions_walk", "dumbbell" to "fitness_center", "sportscourt" to "sports_basketball",
        "soccerball" to "sports_soccer", "basketball" to "sports_basketball", "football" to "sports_football",
        "tennisball" to "sports_tennis", "trophy" to "emoji_events", "medal" to "military_tech",
        "cup.and.saucer" to "local_cafe", "mug" to "coffee", "fork.knife" to "restaurant", "takeoutbag.and.cup.and.straw" to "takeout_dining",
        "wineglass" to "wine_bar", "carrot" to "restaurant", "trash.circle" to "delete",
        // misc / apps
        "ant" to "bug_report", "ladybug" to "bug_report", "terminal" to "terminal", "chevron.left.forwardslash.chevron.right" to "code",
        "curlybraces" to "data_object", "puzzlepiece" to "extension", "gift.circle" to "card_giftcard",
        "app" to "apps", "app.badge" to "apps", "rectangle.stack" to "layers", "square.stack" to "layers",
        "square.3.layers.3d" to "layers", "battery.100percent" to "battery_full", "qrcode" to "qr_code",
        "barcode" to "qr_code_scanner", "touchid" to "fingerprint", "faceid" to "face", "sparkle" to "auto_awesome",
        "wand.and.stars" to "auto_fix_high", "theatermasks" to "theater_comedy", "gamecontroller.fill" to "sports_esports",
    )

    private val shapeSuffixes = listOf(
        ".fill", ".circle", ".square", ".rectangle", ".slash", ".badge.plus", ".badge.minus", ".inverse",
        ".and.arrow.forward", ".bubble", ".diamond", ".shield", ".octagon", ".hexagon", ".seal",
    )

    fun resolve(sfName: String): Resolved {
        val name = sfName.trim()
        candidates(name).forEach { candidate -> table[candidate]?.let { return Resolved(it, isFallback = false) } }
        // Allow Material icon names directly (e.g. "rocket_launch").
        if (MaterialIcons.codepointOf(name) != null) return Resolved(name, isFallback = false)
        return Resolved(FALLBACK, isFallback = true)
    }

    /** Exact name, then progressively simplified variants. */
    private fun candidates(name: String): Sequence<String> = sequence {
        yield(name)
        var current = name
        var changed = true
        while (changed) {
            changed = false
            for (suffix in shapeSuffixes) {
                if (current.endsWith(suffix)) {
                    current = current.removeSuffix(suffix)
                    yield(current)
                    changed = true
                }
            }
        }
        val parts = current.split('.')
        for (n in parts.size - 1 downTo 1) yield(parts.take(n).joinToString("."))
    }

    internal fun mappedMaterialNames(): Set<String> = table.values.toSet()
    internal fun mappedSymbolCount(): Int = table.size
}
