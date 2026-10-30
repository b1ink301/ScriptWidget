package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Not a regular test: runs community Scriptable scripts (outside the repo) and writes a report of
 * which APIs are missing. Skipped unless `-PcompatCorpus=<dir>` is given.
 */
class CompatibilityReport {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Harness-only: network data is a Proxy that tolerates any access, so scripts reach their UI code. */
    private val permissiveNetwork = """
        const __any = () => new Proxy(function () {}, {
          get(t, p) {
            if (p === Symbol.toPrimitive) return (hint) => (hint === "number" ? 1 : "1");
            if (p === Symbol.iterator) return function* () {};
            if (p === "length") return 0;
            if (p === "then") return undefined;
            if (p === "toJSON") return () => null;
            return __any();
          },
          apply() { return __any(); },
          construct() { return __any(); },
        });
        Request.prototype.loadJSON = async function () { return __any(); };
        Request.prototype.loadString = async function () { return "{}"; };
    """.trimIndent()

    private val scriptableGlobals = listOf(
        "Alert", "Calendar", "CalendarEvent", "CallbackURL", "Contact", "ContactsContainer", "ContactsGroup",
        "DatePicker", "Device", "Dictation", "DocumentPicker", "DrawContext", "Mail", "Message", "Pasteboard",
        "Path", "Photos", "QuickLook", "Rect", "RecurrenceRule", "RelativeDateTimeFormatter", "Reminder",
        "Safari", "SFSymbol", "ShareSheet", "Speech", "TextField", "Timer", "UITable", "UITableCell",
        "UITableRow", "URLScheme", "UUID", "WebView", "XMLParser",
    )
        // Only report what the prelude still lacks (the API catalog lists what it defines).
        .filterNot { it in kr.b1ink.scriptwidget.data.api.ApiCatalog.NAMES || it == "TextField" }

    @Test
    fun report() = runTest(timeout = 10.minutes) {
        val corpus = System.getProperty("compat.corpus").orEmpty()
        assumeTrue("set -PcompatCorpus=<dir>", corpus.isNotBlank())

        val scripts = File(corpus).listFiles { f -> f.extension == "js" }.orEmpty().sortedBy { it.name }
        val prelude = Prelude.load() + "\n;\n" + permissiveNetwork
        val lines = mutableListOf<String>()
        val staticCounts = mutableMapOf<String, Int>()
        var ok = 0

        for (file in scripts) {
            val code = file.readText()
            val referenced = scriptableGlobals.filter { Regex("""\b$it\s*[.(]|new\s+$it\b""").containsMatchIn(code) }
            referenced.forEach { staticCounts.merge(it, 1, Int::plus) }

            val files = SandboxedFileSystem(tmp.newFolder())
            // Optional `_docs/<script>/`: the rest of the script's repository, as a user who copied it
            // into Scriptable's Documents folder would have it (for importModule and FileManager reads).
            File(corpus, "_docs/${file.nameWithoutExtension}").takeIf { it.isDirectory }
                ?.copyRecursively(File(files.documentsDirectory), overwrite = true)
            val engine = QuickJsScriptEngine(
                prelude,
                fakeServices(files = files),
                timeoutMillis = 5_000,
            )
            val result = engine.execute(
                code,
                ScriptEnvironment(runsInWidget = true, widgetFamily = WidgetFamily.Medium),
                file.nameWithoutExtension,
            )
            val error = result.error?.lineSequence()?.first()
            val location = result.error?.let { Regex("""${Regex.escape(file.nameWithoutExtension)}\.js:(\d+)""").find(it) }
                ?.groupValues?.get(1)?.toIntOrNull()
            val sourceLine = location?.let { code.lines().getOrNull(it - 1)?.trim()?.take(110) }
            if (error == null && result.widget != null) ok++
            lines += buildString {
                append(if (error == null) (if (result.widget != null) "OK   " else "NOWG ") else "FAIL ")
                append(file.nameWithoutExtension.padEnd(22))
                append(error ?: "")
                sourceLine?.let { append("\n        at L$location: $it") }
                if (referenced.isNotEmpty()) append("\n        uses missing globals: ${referenced.joinToString()}")
            }
        }

        val report = buildString {
            appendLine("Scripts: ${scripts.size}, rendered a widget without error: $ok")
            appendLine()
            appendLine("Missing globals referenced (static scan):")
            staticCounts.entries.sortedByDescending { it.value }.forEach { appendLine("  ${it.key.padEnd(28)} ${it.value}") }
            appendLine()
            lines.forEach(::appendLine)
        }
        File("build/compat-report.txt").writeText(report)
        println(report)
    }
}
