package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kr.b1ink.scriptwidget.data.http.OkHttpGateway
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.WidgetTree
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Not a regular test: runs community Scriptable scripts (outside the repo) and writes a report of
 * which APIs are missing. Skipped unless `-PcompatCorpus=<dir>` is given.
 *
 * `-PcompatNetwork` picks where network data comes from:
 * - `proxy` (default): a Proxy that tolerates any access, to find missing APIs.
 * - `record`: the real network; each script's traffic is saved to `<corpus>/_http/<script>.json`.
 * - `replay`: the saved traffic, with the clock set to the recording time and a seeded
 *   `Math.random`, so the same script gives the same widget every run.
 *
 * Each script's result (widget tree or error) is compared with `<corpus>/_golden/<script>.json`
 * and reported as changed if it differs; `-PcompatUpdateGolden` writes the current results there.
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

    /** Harness-only: a fixed clock and a seeded Math.random for [now], so replayed runs are repeatable. */
    private fun deterministic(now: Long) = """
        (() => {
          const RealDate = Date;
          function FixedDate(...a) {
            if (!new.target) return new RealDate($now).toString();
            return a.length ? new RealDate(...a) : new RealDate($now);
          }
          FixedDate.prototype = RealDate.prototype;
          FixedDate.now = () => $now;
          FixedDate.parse = RealDate.parse;
          FixedDate.UTC = RealDate.UTC;
          globalThis.Date = FixedDate;
          let seed = 42;
          Math.random = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
        })();
    """.trimIndent()

    private val prettyJson = Json { prettyPrint = true }

    /** What a run produced, as compared with the golden file: the widget tree, or the error. */
    private fun snapshot(error: String?, widgetJson: String?): String {
        if (error != null) return "ERROR: $error\n"
        if (widgetJson == null) return "NO WIDGET\n"
        // Image paths carry the run's cache key, which changes every run.
        val stable = widgetJson.replace(Regex("""/cache/[^"]*/img_"""), "/cache/img_")
        return prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(stable)) + "\n"
    }

    @Test
    fun report() = runTest(timeout = 60.minutes) {
        val corpus = System.getProperty("compat.corpus").orEmpty()
        assumeTrue("set -PcompatCorpus=<dir>", corpus.isNotBlank())
        val network = System.getProperty("compat.network").orEmpty().ifBlank { "proxy" }
        require(network in listOf("proxy", "record", "replay")) { "compatNetwork must be proxy, record or replay" }
        val updateGolden = System.getProperty("compat.updateGolden") == "true"
        val httpDir = File(corpus, "_http")
        val goldenDir = File(corpus, "_golden")
        val treesDir = File("build/compat-trees").apply { deleteRecursively(); mkdirs() }
        val liveHttp by lazy { OkHttpGateway.cached(tmp.newFolder("http-cache")) }

        val scripts = File(corpus).listFiles { f -> f.extension == "js" }.orEmpty().sortedBy { it.name }
        val lines = mutableListOf<String>()
        val staticCounts = mutableMapOf<String, Int>()
        val changed = mutableListOf<String>()
        var ok = 0
        var replayed = 0

        for (file in scripts) {
            val code = file.readText()
            val referenced = scriptableGlobals.filter { Regex("""\b$it\s*[.(]|new\s+$it\b""").containsMatchIn(code) }
            referenced.forEach { staticCounts.merge(it, 1, Int::plus) }

            val files = SandboxedFileSystem(tmp.newFolder())
            // Optional `_docs/<script>/`: the rest of the script's repository, as a user who copied it
            // into Scriptable's Documents folder would have it (for importModule and FileManager reads).
            File(corpus, "_docs/${file.nameWithoutExtension}").takeIf { it.isDirectory }
                ?.copyRecursively(File(files.documentsDirectory), overwrite = true)
            val recordingFile = File(httpDir, "${file.nameWithoutExtension}.json")
            val recorder = if (network == "record") RecordingHttpGateway(liveHttp) else null
            val replay = if (network == "replay" && recordingFile.isFile) ReplayHttpGateway(ReplayHttpGateway.load(recordingFile)) else null
            val http: HttpGateway = recorder ?: replay ?: FakeHttpGateway()
            val prelude = Prelude.load() + "\n;\n" + when {
                recorder != null -> ""
                replay != null -> deterministic(ReplayHttpGateway.load(recordingFile).recordedAt)
                else -> permissiveNetwork
            }
            if (replay != null) replayed++
            val engine = QuickJsScriptEngine(
                prelude,
                fakeServices(http = http, files = files),
                timeoutMillis = if (recorder != null) 30_000 else 5_000,
            )
            val result = engine.execute(
                code,
                ScriptEnvironment(runsInWidget = true, widgetFamily = WidgetFamily.Medium),
                file.nameWithoutExtension,
            )
            recorder?.save(recordingFile)
            val error = result.error?.lineSequence()?.first()

            val current = snapshot(error, result.widget?.let(WidgetTree::encode))
            File(treesDir, "${file.nameWithoutExtension}.json").writeText(current)
            val golden = File(goldenDir, "${file.nameWithoutExtension}.json")
            val goldenState = when {
                updateGolden -> golden.also { it.parentFile.mkdirs() }.writeText(current).let { "" }
                !golden.isFile -> ""
                golden.readText() == current -> ""
                else -> " [changed]".also { changed += file.nameWithoutExtension }
            }
            val location = result.error?.let { Regex("""${Regex.escape(file.nameWithoutExtension)}\.js:(\d+)""").find(it) }
                ?.groupValues?.get(1)?.toIntOrNull()
            val sourceLine = location?.let { code.lines().getOrNull(it - 1)?.trim()?.take(110) }
            if (error == null && result.widget != null) ok++
            lines += buildString {
                append(if (error == null) (if (result.widget != null) "OK   " else "NOWG ") else "FAIL ")
                append(file.nameWithoutExtension.padEnd(22))
                append(error ?: "")
                append(goldenState)
                replay?.misses?.takeIf { it.isNotEmpty() }?.let { append("\n        not recorded: ${it.distinct().take(3).joinToString()}") }
                sourceLine?.let { append("\n        at L$location: $it") }
                if (referenced.isNotEmpty()) append("\n        uses missing globals: ${referenced.joinToString()}")
            }
        }

        val report = buildString {
            appendLine("Scripts: ${scripts.size}, rendered a widget without error: $ok (network: $network)")
            if (network == "replay") appendLine("Replayed from recordings: $replayed (the rest used the permissive proxy)")
            if (changed.isNotEmpty()) {
                appendLine("Changed from _golden: ${changed.size} (diff $goldenDir with ${treesDir.absolutePath})")
                changed.forEach { appendLine("  $it") }
            }
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
