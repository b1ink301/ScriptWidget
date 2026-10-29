package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * QuickJS has no Intl: number/date formatting is backed by java.text / java.time.
 * Default locale en-US, default time zone Asia/Seoul. 1700000000000 = 2023-11-15 07:13:20 KST.
 */
class IntlContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private suspend fun out(script: String, locale: Locale = Locale.US): String {
        val result = QuickJsScriptEngine(
            Prelude.load(),
            fakeServices(files = SandboxedFileSystem(tmp.newFolder()), locale = locale),
            timeoutMillis = 2_000,
        ).execute(script, ScriptEnvironment(runsInWidget = false), "intl")
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    @Test
    fun `numbers use the device locale by default`() = runTest {
        assertEquals("1,234,567.891", out("console.log((1234567.891).toLocaleString());"))
        assertEquals("1.234.567,891", out("console.log((1234567.891).toLocaleString());", Locale.GERMANY))
    }

    @Test
    fun `number format options`() = runTest {
        assertEquals(
            listOf("1,234,567.9", "₩1,234,568", "26%", "1234.50", "1.234,5").joinToString("\n"),
            out(
                """
                console.log((1234567.891).toLocaleString("en-US", { maximumFractionDigits: 1 }));
                console.log(new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }).format(1234567.891));
                console.log((0.256).toLocaleString("en-US", { style: "percent" }));
                console.log((1234.5).toLocaleString("en-US", { useGrouping: false, minimumFractionDigits: 2 }));
                console.log(new Intl.NumberFormat("de-DE").format(1234.5));
                """
            ),
        )
    }

    @Test
    fun `dates use styles, locale and time zone`() = runTest {
        assertEquals(
            listOf("Nov 15, 2023", "2023년 11월 15일 수요일", "10:13 PM", "7:13:20 AM").joinToString("\n"),
            out(
                """
                const d = new Date(1700000000000);
                console.log(d.toLocaleDateString("en-US", { dateStyle: "medium" }));
                console.log(new Intl.DateTimeFormat("ko-KR", { dateStyle: "full" }).format(d));
                console.log(new Intl.DateTimeFormat("en-US", { timeStyle: "short", timeZone: "UTC" }).format(d));
                console.log(d.toLocaleTimeString());
                """
            ),
        )
    }

    @Test
    fun `DateFormatter formats and parses patterns like Scriptable`() = runTest {
        assertEquals(
            listOf("2023-11-15 07:13", "1699999980000", "Wednesday", "null").joinToString("\n"),
            out(
                """
                const df = new DateFormatter();
                df.dateFormat = "yyyy-MM-dd HH:mm";
                const s = df.string(new Date(1700000000000));
                console.log(s);
                console.log(df.date(s).getTime());
                df.dateFormat = "EEEE";
                console.log(df.string(new Date(1700000000000)));
                df.dateFormat = "yyyy-MM-dd";
                console.log(df.date("not a date"));
                """
            ),
        )
    }

    @Test
    fun `DateFormatter styles and locale`() = runTest {
        assertEquals(
            "2023. 11. 15.",
            out(
                """
                const df = new DateFormatter();
                df.locale = "ko-KR";
                df.useMediumDateStyle();
                df.useNoTimeStyle();
                console.log(df.string(new Date(1700000000000)));
                """
            ),
        )
    }

    // --- Intl.DateTimeFormat component options (hour, minute, weekday, …), like browsers ---

    @Test
    fun `Intl constructors work without new and report resolved options`() = runTest {
        assertEquals(
            "1,234\nAsia/Seoul\nen-US",
            out(
                """
                console.log(Intl.NumberFormat("en-US").format(1234));
                const o = Intl.DateTimeFormat().resolvedOptions();
                console.log(o.timeZone);
                console.log(o.locale);
                """
            ),
        )
    }

    @Test
    fun `Korean component formats`() = runTest {
        assertEquals(
            listOf("11월 15일", "수", "오전 7:13", "2023년 11월 15일 (수)", "11월 15일 수요일", "17:13").joinToString("\n"),
            out(
                """
                const d = new Date(1700000000000);
                console.log(d.toLocaleDateString("ko-KR", { month: "long", day: "numeric" }));
                console.log(d.toLocaleDateString("ko-KR", { weekday: "short" }));
                console.log(d.toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" }));
                console.log(d.toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric", weekday: "short" }));
                console.log(d.toLocaleDateString("ko-KR", { month: "long", day: "numeric", weekday: "long" }));
                console.log(new Intl.DateTimeFormat("ko-KR", { timeZone: "America/New_York", hour: "2-digit", minute: "2-digit", hour12: false }).format(d));
                """
            ),
        )
    }

    @Test
    fun `English component formats`() = runTest {
        assertEquals(
            listOf("Nov 15", "7:13 AM", "Wednesday, November 15", "11/15/2023", "07:13:20").joinToString("\n"),
            out(
                """
                const d = new Date(1700000000000);
                console.log(d.toLocaleDateString("en-US", { month: "short", day: "numeric" }));
                console.log(d.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }));
                console.log(d.toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" }));
                console.log(d.toLocaleDateString("en-US", { year: "numeric", month: "numeric", day: "numeric" }));
                console.log(d.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }));
                """
            ),
        )
    }
}
