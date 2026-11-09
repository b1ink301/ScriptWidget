package kr.b1ink.scriptwidget.data.diagnostics

import kr.b1ink.scriptwidget.domain.model.CrashReport
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashLogStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dir by lazy { File(tmp.root, "crashes") }
    private val store by lazy { CrashLogStore(dir, max = 3) }

    @Test
    fun `keeps crashes across restarts, newest first`() {
        store.record(CrashReport(time = 1, kind = "crash", message = "first"))
        store.record(CrashReport(time = 2, kind = "crash", message = "second"))

        assertEquals(listOf("second", "first"), CrashLogStore(dir).all().map { it.message })
    }

    @Test
    fun `keeps only the latest few`() {
        (1..5L).forEach { store.record(CrashReport(time = it, kind = "crash", message = "c$it")) }

        assertEquals(listOf("c5", "c4", "c3"), store.all().map { it.message })
        assertEquals(3, dir.listFiles()!!.size)
    }

    @Test
    fun `a damaged file is skipped`() {
        store.record(CrashReport(time = 1, kind = "crash", message = "ok"))
        File(dir, "2.json").writeText("{not json")

        assertEquals(listOf("ok"), store.all().map { it.message })
    }

    @Test
    fun `describes an uncaught exception with its cause`() {
        val error = IllegalStateException("outer", RuntimeException("inner"))

        val report = CrashLogStore.reportOf(Thread.currentThread().apply { name = "worker-1" }, error, now = 42)

        assertEquals(42, report.time)
        assertEquals("crash", report.kind)
        assertEquals("worker-1", report.thread)
        assertEquals("IllegalStateException: outer", report.message)
        assertContains(report.stackTrace, "Caused by: java.lang.RuntimeException: inner")
        assertTrue(report.stackTrace.lines().first().startsWith("at "), report.stackTrace)
    }

    @Test
    fun `clear removes everything`() {
        store.record(CrashReport(time = 1, kind = "crash", message = "x"))
        store.clear()

        assertEquals(emptyList(), store.all())
    }
}
