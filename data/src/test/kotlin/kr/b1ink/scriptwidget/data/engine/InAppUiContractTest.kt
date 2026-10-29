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
import kotlin.test.assertTrue

/** Alert, TextField, UITable, DatePicker and QuickLook through the UiPresenter port. */
class InAppUiContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val ui = FakeUiPresenter()
    private val images = FakeImageStore()
    private val http = FakeHttpGateway()

    private fun engine(timeoutMillis: Long = 2_000, interactiveTimeoutMillis: Long = 5_000) = QuickJsScriptEngine(
        Prelude.load(),
        fakeServices(http = http, images = images, files = SandboxedFileSystem(tmp.newFolder()), ui = ui),
        timeoutMillis = timeoutMillis,
        interactiveTimeoutMillis = interactiveTimeoutMillis,
    )

    private suspend fun run(script: String, inWidget: Boolean = false) =
        engine().execute(script, ScriptEnvironment(runsInWidget = inWidget), "ui")

    private suspend fun out(script: String): String {
        val result = run(script)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    // --- Alert & TextField ---

    @Test
    fun `alert actions and text fields round trip`() = runTest {
        ui.alertAnswers += AlertResult(index = 1, texts = listOf("Seoul", "s3cr3t"))
        val log = out(
            """
            const a = new Alert();
            a.title = "Settings";
            a.message = "Pick a city";
            a.addAction("Cancel-ish");
            a.addDestructiveAction("Delete");
            a.addCancelAction("Cancel");
            const city = a.addTextField("City", "Busan");
            const pw = a.addSecureTextField("Password");
            city.setNumberPadKeyboard();
            const i = await a.presentAlert();
            console.log(i, a.textFieldValue(0), a.textFieldValue(1), city instanceof TextField);
            """
        )
        assertEquals("1 Seoul s3cr3t true", log)
        assertEquals(
            AlertSpec(
                title = "Settings", message = "Pick a city", style = "alert",
                actions = listOf(AlertAction("Cancel-ish", "default"), AlertAction("Delete", "destructive"), AlertAction("Cancel", "cancel")),
                textFields = listOf(AlertTextField("City", "Busan", false), AlertTextField("Password", "", true)),
            ),
            ui.alerts.single(),
        )
    }

    @Test
    fun `textFieldValue without an index reads the first field, like Scriptable`() = runTest {
        ui.alertAnswers += AlertResult(index = 0, texts = listOf("Netflix"))

        val result = engine().execute(
            """
            const a = new Alert();
            a.addTextField("placeholder", "");
            a.addAction("OK");
            await a.presentAlert();
            console.log(a.textFieldValue());
            """,
            ScriptEnvironment(runsInWidget = false), "alert",
        )

        assertEquals("Netflix", result.logs.single().message, result.error)
    }

    @Test
    fun `cancel returns -1 and sheets are requested as sheets`() = runTest {
        ui.alertAnswers += AlertResult(index = -1, texts = emptyList())
        assertEquals("-1", out("""const a = new Alert(); a.addAction("A"); console.log(await a.presentSheet());"""))
        assertEquals("sheet", ui.alerts.single().style)
    }

    @Test
    fun `present defaults to an alert`() = runTest {
        ui.alertAnswers += AlertResult(index = 0, texts = emptyList())
        out("""const a = new Alert(); a.addAction("OK"); await a.present();""")
        assertEquals("alert", ui.alerts.single().style)
    }

    @Test
    fun `UI cannot be presented during a widget refresh`() = runTest {
        val result = run("""const a = new Alert(); a.addAction("OK"); await a.present();""", inWidget = true)
        assertContains(assertNotNull(result.error), "widget")
        assertTrue(ui.alerts.isEmpty())
    }

    @Test
    fun `waiting for the user does not count against the widget timeout`() = runTest {
        ui.delayMillis = 2_500
        ui.alertAnswers += AlertResult(index = 0, texts = emptyList())
        val result = engine(timeoutMillis = 1_000, interactiveTimeoutMillis = 10_000)
            .execute("""const a = new Alert(); a.addAction("OK"); console.log(await a.present());""", ScriptEnvironment(runsInWidget = false), "ui")
        assertNull(result.error, result.error)
        assertEquals("0", result.logs.single().message)
    }

    // --- UITable ---

    @Test
    fun `table rows and cells are sent to the UI`() = runTest {
        out(
            """
            const t = new UITable();
            t.showSeparators = true;
            const header = new UITableRow();
            header.isHeader = true;
            header.addText("Name", "subtitle").widthWeight = 2;
            header.backgroundColor = new Color("#eeeeee");
            t.addRow(header);
            const r = new UITableRow();
            r.height = 60;
            const txt = UITableCell.text("Seoul", "37.5");
            txt.titleColor = Color.red();
            txt.rightAligned();
            r.addCell(txt);
            r.addButton("Go");
            t.addRow(r);
            await t.present(true);
            """
        )
        val spec = ui.tables.single()
        assertTrue(spec.showSeparators)
        assertTrue(spec.fullscreen)
        assertEquals(2, spec.rows.size)
        assertTrue(spec.rows[0].isHeader)
        assertEquals("#FFEEEEEE", spec.rows[0].backgroundColor)
        assertEquals(TableCell(type = "text", title = "Name", subtitle = "subtitle", widthWeight = 2f), spec.rows[0].cells.single())
        assertEquals(60f, spec.rows[1].height)
        assertEquals(TableCell(type = "text", title = "Seoul", subtitle = "37.5", titleColor = "#FFFF3B30", align = "right"), spec.rows[1].cells[0])
        assertEquals("button", spec.rows[1].cells[1].type)
    }

    @Test
    fun `row selection and button taps call back into the script until dismissed`() = runTest {
        ui.tableEvents += TableEvent.Selected(row = 1)
        ui.tableEvents += TableEvent.CellTapped(row = 0, cell = 1)
        ui.tableEvents += TableEvent.Dismissed
        val log = out(
            """
            const t = new UITable();
            const a = new UITableRow();
            a.addText("A");
            const b = a.addButton("tap me");
            b.onTap = () => console.log("button");
            t.addRow(a);
            const r = new UITableRow();
            r.addText("B");
            r.dismissOnSelect = false;
            r.onSelect = (i) => { console.log("selected", i); r.cells[0].title = "B!"; t.reload(); };
            t.addRow(r);
            await t.present();
            console.log("done");
            """
        )
        assertEquals("selected 1\nbutton\ndone", log)
        assertEquals(3, ui.tables.size)
        assertEquals("B!", ui.tables[1].rows[1].cells.single().title)
    }

    @Test
    fun `rows dismiss the table on select by default, like Scriptable`() = runTest {
        ui.tableEvents += TableEvent.Selected(row = 0)
        out("""const t = new UITable(); const r = new UITableRow(); r.addText("x"); r.onSelect = () => {}; t.addRow(r); await t.present();""")
        assertEquals(1, ui.tableDismissals)
    }

    @Test
    fun `dismissOnSelect closes the table after the handler`() = runTest {
        ui.tableEvents += TableEvent.Selected(row = 0)
        val log = out(
            """
            const t = new UITable();
            const r = new UITableRow();
            r.addText("pick");
            r.dismissOnSelect = true;
            r.onSelect = () => console.log("picked");
            t.addRow(r);
            await t.present();
            console.log("after");
            """
        )
        assertEquals("picked\nafter", log)
        assertEquals(1, ui.tables.size)
        assertEquals(1, ui.tableDismissals)
    }

    @Test
    fun `table image cells reference cached images`() = runTest {
        out(
            """
            const img = await new Request("https://img").loadImage();
            const t = new UITable(); const r = new UITableRow(); r.addImage(img).centerAligned(); t.addRow(r);
            await t.present();
            """
        )
        val cell = ui.tables.single().rows.single().cells.single()
        assertEquals("image", cell.type)
        assertEquals("center", cell.align)
        assertTrue(cell.src!!.startsWith("/cache/"))
    }

    @Test
    fun `a handler error closes the open table so the screen isn't left stuck`() = runTest {
        ui.tableEvents += TableEvent.Selected(row = 0)
        val result = run(
            """
            const t = new UITable();
            const r = new UITableRow();
            r.addText("broken");
            r.dismissOnSelect = false;
            r.onSelect = () => { null.boom(); };
            t.addRow(r);
            await t.present();
            """
        )
        assertNotNull(result.error)
        assertEquals(1, ui.tableDismissals)
    }

    @Test
    fun `nested tables - closing the inner one returns to the outer one`() = runTest {
        ui.tableEvents += TableEvent.Selected(row = 0)
        ui.tableEvents += TableEvent.Dismissed
        ui.tableEvents += TableEvent.Dismissed
        val log = out(
            """
            const outer = new UITable();
            const r = new UITableRow();
            r.addText("open inner");
            r.dismissOnSelect = false;
            r.onSelect = async () => {
              const inner = new UITable();
              const ir = new UITableRow(); ir.addText("inner"); inner.addRow(ir);
              await inner.present();
              console.log("inner closed");
            };
            outer.addRow(r);
            await outer.present();
            console.log("outer closed");
            """
        )
        assertEquals("inner closed\nouter closed", log)
        assertEquals(listOf("open inner", "inner", "open inner"), ui.tables.map { it.rows.single().cells.single().title })
        assertEquals(0, ui.tableDismissals)
    }

    @Test
    fun `imageAtURL cells load the image before the table is shown`() = runTest {
        out(
            """
            const t = new UITable(); const r = new UITableRow();
            r.height = 200;
            r.addCell(UITableCell.imageAtURL("https://img")).centerAligned();
            t.addRow(r);
            await t.present();
            """
        )
        val cell = ui.tables.single().rows.single().cells.single()
        assertEquals("image", cell.type)
        assertTrue(cell.src!!.startsWith("/cache/"))
    }

    @Test
    fun `imageAtURL cells that fail to load are shown empty instead of failing`() = runTest {
        http.bytes = { throw java.io.IOException("404") }
        out(
            """
            const t = new UITable(); const r = new UITableRow();
            r.addCell(UITableCell.imageAtURL("https://missing.invalid/x.png"));
            t.addRow(r);
            await t.present();
            """
        )
        val cell = ui.tables.single().rows.single().cells.single()
        assertEquals("image", cell.type)
        assertNull(cell.src)
    }

    // --- DatePicker ---

    @Test
    fun `date picker returns a Date and passes its constraints`() = runTest {
        ui.dateAnswers += 1_700_000_000_000L
        val log = out(
            """
            const p = new DatePicker();
            p.initialDate = new Date(1600000000000);
            p.minimumDate = new Date(1500000000000);
            const d = await p.pickDateAndTime();
            console.log(d instanceof Date, d.getTime());
            """
        )
        assertEquals("true 1700000000000", log)
        assertEquals(DatePickerSpec(mode = "dateAndTime", initial = 1_600_000_000_000L, min = 1_500_000_000_000L), ui.datePickers.single())
    }

    @Test
    fun `cancelling the date picker rejects`() = runTest {
        ui.dateAnswers += null
        val result = run("""await new DatePicker().pickDate();""")
        assertContains(assertNotNull(result.error), "cancel")
    }

    @Test
    fun `countdown duration is returned in seconds`() = runTest {
        ui.dateAnswers += 90_000L
        assertEquals("90", out("""const p = new DatePicker(); p.countdownDuration = 60; console.log(await p.pickCountdownDuration());"""))
        assertEquals(DatePickerSpec(mode = "countdown", countdownSeconds = 60), ui.datePickers.single())
    }

    // --- QuickLook ---

    @Test
    fun `quick look shows images and text`() = runTest {
        out(
            """
            await QuickLook.present("hello", false);
            await QuickLook.present(await new Request("https://img").loadImage(), true);
            await QuickLook.present({a: 1});
            """
        )
        assertEquals(QuickLookSpec(text = "hello", fullscreen = false), ui.quickLooks[0])
        assertTrue(ui.quickLooks[1].src!!.startsWith("/cache/"))
        assertTrue(ui.quickLooks[1].fullscreen)
        assertEquals(QuickLookSpec(text = "{\n  \"a\": 1\n}", fullscreen = false), ui.quickLooks[2])
    }
}
