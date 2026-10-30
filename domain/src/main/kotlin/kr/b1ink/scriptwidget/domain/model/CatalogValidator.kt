package kr.b1ink.scriptwidget.domain.model

sealed interface CatalogProblem {
    val entryId: String
    val message: String

    data class Error(override val entryId: String, override val message: String) : CatalogProblem

    data class Warning(override val entryId: String, override val message: String) : CatalogProblem
}

/**
 * Checks a gallery catalog (`catalog.json` / `index.json`): entry fields, and that the permissions an
 * entry shows match the APIs its script uses, so the gallery never hides what a script reads.
 */
object CatalogValidator {
    val SIZES = setOf("small", "medium", "large")

    /** Permission → API uses that need it. */
    private val USES = mapOf(
        "calendar" to listOf("CalendarEvent.", "Calendar.forEvents", "Calendar.defaultForEvents", "Calendar.forEventsByTitle"),
        "contacts" to listOf("Contact.", "ContactsContainer.", "ContactsGroup."),
        "location" to listOf("Location."),
        "network" to listOf("new Request(", "Request(", "WebView.loadURL", ".loadURL("),
        "notification" to listOf("new Notification(", "Notification.schedule"),
        "photos" to listOf("Photos."),
    )
    val PERMISSIONS: Set<String> = USES.keys

    /** The permissions [code] needs, judged from the APIs it calls. */
    fun usedPermissions(code: String): Set<String> = USES.filterValues { uses -> uses.any { it in code } }.keys

    /** @param code the script of an entry, or null when it can't be found. */
    fun validate(catalog: Catalog, code: (entryId: String) -> String?): List<CatalogProblem> {
        val problems = mutableListOf<CatalogProblem>()
        val seen = mutableSetOf<String>()
        for (entry in catalog.entries) {
            fun error(message: String) = problems.add(CatalogProblem.Error(entry.id, message))
            if (!seen.add(entry.id)) error("duplicate id")
            if (!ScriptName.isValid(entry.name)) error("name \"${entry.name}\" is not a valid script name")
            if (entry.description.isBlank()) error("no description")
            if (entry.sizes.isEmpty()) error("no sizes")
            entry.sizes.filterNot { it in SIZES }.forEach { error("unknown size \"$it\"") }
            entry.permissions.filterNot { it in PERMISSIONS }.forEach { error("unknown permission \"$it\"") }
            val script = code(entry.id)
            if (script == null) {
                error("script ${entry.script} not found")
                continue
            }
            val used = usedPermissions(script)
            (used - entry.permissions.toSet()).sorted().forEach { error("uses $it but does not declare it in permissions") }
            (entry.permissions.toSet().intersect(PERMISSIONS) - used).sorted().forEach {
                problems.add(CatalogProblem.Warning(entry.id, "declares $it but does not use it"))
            }
        }
        return problems
    }
}
