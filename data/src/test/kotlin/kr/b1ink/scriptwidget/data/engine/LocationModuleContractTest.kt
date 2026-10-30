package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Scriptable's Location and importModule() on real QuickJS. */
class LocationModuleContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val location = FakeLocationProvider()
    private var modules = mapOf<String, String>()

    private suspend fun run(script: String) = QuickJsScriptEngine(
        Prelude.load(),
        fakeServices(files = SandboxedFileSystem(tmp.newFolder()), location = location, modules = MapModuleSource(modules)),
        timeoutMillis = 2_000,
    ).execute(script, ScriptEnvironment(runsInWidget = false), "main")

    private suspend fun logOf(script: String): String {
        val result = run(script)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    // --- Location ---

    @Test
    fun `current location has Scriptable's fields`() = runTest {
        val out = logOf(
            """
            const l = await Location.current();
            console.log(l.latitude, l.longitude, l.altitude, l.horizontalAccuracy, l.verticalAccuracy, l.timestamp instanceof Date);
            """
        )
        assertEquals("37.5665 126.978 38 5 3 true", out)
    }

    @Test
    fun `accuracy setters are passed to the provider`() = runTest {
        logOf(
            """
            await Location.current();
            Location.setAccuracyToThreeKilometers(); await Location.current();
            Location.setAccuracyToBest(); await Location.current();
            Location.setAccuracyToHundredMeters(); await Location.current();
            """
        )
        assertEquals(
            listOf(LocationAccuracy.Best, LocationAccuracy.ThreeKilometers, LocationAccuracy.Best, LocationAccuracy.HundredMeters),
            location.requestedAccuracies,
        )
    }

    @Test
    fun `unavailable location rejects with the reason`() = runTest {
        location.failure = LocationUnavailableException("Location permission not granted", permissionDenied = true)
        val out = logOf("try { await Location.current(); } catch (e) { console.log(e.message); }")
        assertContains(out, "Location permission not granted")
    }

    @Test
    fun `reverseGeocode returns placemarks`() = runTest {
        location.placemarks = listOf(Placemark(locality = "Seoul", country = "South Korea", isoCountryCode = "KR"))
        val out = logOf(
            """
            const [p] = await Location.reverseGeocode(37.5, 127.0);
            console.log(p.locality, p.country, p.isoCountryCode);
            """
        )
        assertEquals("Seoul South Korea KR", out)
    }

    // --- importModule ---

    @Test
    fun `imports module exports`() = runTest {
        modules = mapOf("math" to "module.exports.add = (a, b) => a + b; exports.pi = 3;")
        assertEquals("5 3", logOf("""const m = importModule("math"); console.log(m.add(2, 3), m.pi);"""))
    }

    @Test
    fun `module exports can be replaced and js extension is accepted`() = runTest {
        modules = mapOf("greet" to "module.exports = (n) => `hi ${'$'}{n}`;")
        assertEquals("hi kim", logOf("""console.log(importModule("greet.js")("kim"));"""))
    }

    @Test
    fun `modules are evaluated once and can import each other`() = runTest {
        modules = mapOf(
            "counter" to "globalThis.loads = (globalThis.loads || 0) + 1; module.exports = { n: 1 };",
            "uses" to "const c = importModule('counter'); module.exports = c.n + 1;",
        )
        assertEquals("2 1 1", logOf("""const a = importModule("uses"); const b = importModule("counter"); console.log(a, b.n, loads);"""))
    }

    @Test
    fun `scripts and modules know their file, like Scriptable's module global`() = runTest {
        modules = mapOf("lib" to "module.exports = module.filename;")
        val out = logOf(
            """
            const fm = FileManager.local();
            console.log(module.filename === fm.joinPath(fm.documentsDirectory(), "main.js"));
            console.log(typeof module.exports, typeof exports);
            console.log(importModule("lib") === fm.joinPath(fm.documentsDirectory(), "lib.js"));
            console.log(fm.isFileStoredIniCloud(module.filename), module.filename.includes("Documents/iCloud~"));
            """.trimIndent(),
        )
        assertEquals("true\nobject object\ntrue\nfalse false", out)
    }

    @Test
    fun `top-level code runs inside a function, so arguments exists`() = runTest {
        assertEquals("0 {\"a\":1}", logOf("""const cfg = Object.assign({}, { a: 1 }, arguments[0]); console.log(arguments.length, JSON.stringify(cfg));"""))
    }

    @Test
    fun `modules saved in Documents load by relative or absolute path`() = runTest {
        val out = logOf(
            """
            const fm = FileManager.local();
            const dir = fm.joinPath(fm.documentsDirectory(), "MyWidget");
            fm.createDirectory(dir, true);
            fm.writeString(fm.joinPath(dir, "util.js"), "module.exports = { twice: (n) => n * 2 };");
            console.log(importModule("MyWidget/util").twice(2), importModule(fm.joinPath(dir, "util.js")).twice(3));
            """.trimIndent(),
        )
        assertEquals("4 6", out)
    }

    @Test
    fun `missing modules throw`() = runTest {
        assertContains(assertNotNull(run("""importModule("nope");""").error), "nope")
    }
}
