package kr.b1ink.scriptwidget.data.design

import kr.b1ink.scriptwidget.domain.model.DataBinding
import kr.b1ink.scriptwidget.domain.model.DesignDate
import kr.b1ink.scriptwidget.domain.model.DesignElement
import kr.b1ink.scriptwidget.domain.model.DesignImage
import kr.b1ink.scriptwidget.domain.model.DesignSpacer
import kr.b1ink.scriptwidget.domain.model.DesignStack
import kr.b1ink.scriptwidget.domain.model.DesignSymbol
import kr.b1ink.scriptwidget.domain.model.DesignText
import kr.b1ink.scriptwidget.domain.model.WidgetDesign
import kr.b1ink.scriptwidget.domain.repository.DesignScriptGenerator

/**
 * Writes a design as a plain Scriptable script: data loading first, then one widget call per element,
 * so every element becomes exactly one node at the same path (the designer relies on it to select
 * elements by tapping the preview).
 */
class JsDesignScriptGenerator(private val texts: GeneratorTexts = GeneratorTexts.KOREAN) : DesignScriptGenerator {

    override fun generate(design: WidgetDesign): String = buildString {
        texts.header.forEach { appendLine("// $it") }
        val bindings = design.bindings()
        if (bindings.isNotEmpty()) {
            appendLine()
            append(dataHelpers(texts, bindings))
            bindings.forEachIndexed { i, binding -> appendLine("await load(\"d$i\", async () => ${loader(binding)});") }
        }
        if (design.bookmarks().isNotEmpty()) {
            appendLine()
            append(bookmarkHelper(texts))
        }
        appendLine()
        appendLine("const w = new ListWidget();")
        design.background.gradient?.takeIf { it.size >= 2 }?.let { colors ->
            appendLine("const bg = new LinearGradient();")
            appendLine("bg.colors = [${colors.joinToString { color(it) }}];")
            appendLine("bg.locations = [${colors.indices.joinToString { num(it / (colors.size - 1f)) }}];")
            appendLine("w.backgroundGradient = bg;")
        } ?: design.background.color?.let { appendLine("w.backgroundColor = ${color(it)};") }
        design.background.image?.let { appendLine("const bgImage = bookmarkImage(${str(it)});\nif (bgImage) w.backgroundImage = bgImage;") }
        design.padding?.let { appendLine("w.setPadding(${num(it)}, ${num(it)}, ${num(it)}, ${num(it)});") }
        if (design.spacing > 0) appendLine("w.spacing = ${num(design.spacing)};")
        design.url?.let { appendLine("w.url = ${str(it)};") }
        design.refreshMinutes?.let { appendLine("w.refreshAfterDate = new Date(Date.now() + $it * 60 * 1000);") }
        val names = Names()
        design.children.forEach { element(it, "w", names, bindings) }
        appendLine()
        appendLine("Script.setWidget(w);")
        appendLine("if (!config.runsInWidget) await w.presentMedium();")
        appendLine("Script.complete();")
    }

    private fun StringBuilder.element(element: DesignElement, parent: String, names: Names, bindings: List<DataBinding>) {
        appendLine()
        when (element) {
            is DesignText -> {
                val v = names.next("text")
                val parts = element.text.split(VALUE).map(::str)
                val value = element.data?.let { "String(data.d${bindings.indexOf(it)})" }
                val text = if (value == null) str(element.text) else parts.joinToString(" + $value + ")
                appendLine("const $v = $parent.addText($text);")
                appendLine("$v.font = ${font(element.weight, element.size, element.font)};")
                element.color?.let { appendLine("$v.textColor = ${color(it)};") }
                align(v, element.align)
                if (element.lineLimit > 0) appendLine("$v.lineLimit = ${element.lineLimit};")
                if (element.minimumScaleFactor < 1f) appendLine("$v.minimumScaleFactor = ${num(element.minimumScaleFactor)};")
                element.url?.let { appendLine("$v.url = ${str(it)};") }
            }
            is DesignDate -> {
                val v = names.next("date")
                appendLine("const $v = $parent.addDate(new Date());")
                appendLine("$v.${DATE_STYLES[element.style] ?: "applyTimeStyle"}();")
                appendLine("$v.font = ${font(element.weight, element.size)};")
                element.color?.let { appendLine("$v.textColor = ${color(it)};") }
                align(v, element.align)
            }
            is DesignSymbol -> {
                val v = names.next("symbol")
                appendLine("const $v = $parent.addImage(SFSymbol.named(${str(element.name)}).image);")
                appendLine("$v.imageSize = new Size(${num(element.size)}, ${num(element.size)});")
                element.color?.let { appendLine("$v.tintColor = ${color(it)};") }
            }
            is DesignImage -> {
                val v = names.next("image")
                // A placeholder keeps the element's place when the image can't be loaded.
                val image = element.bookmark?.let { "bookmarkImage(${str(it)}) ?? $PLACEHOLDER" }
                    ?: "await new Request(${str(element.url)}).loadImage().catch(() => $PLACEHOLDER)"
                appendLine("const $v = $parent.addImage($image);")
                element.size?.let { appendLine("$v.imageSize = new Size(${num(it)}, ${num(it)});") }
                if (element.cornerRadius > 0) appendLine("$v.cornerRadius = ${num(element.cornerRadius)};")
            }
            is DesignSpacer -> appendLine("$parent.addSpacer(${element.length?.let(::num).orEmpty()});")
            is DesignStack -> {
                val v = names.next("stack")
                appendLine("const $v = $parent.addStack();")
                appendLine(if (element.dir == "v") "$v.layoutVertically();" else "$v.layoutHorizontally();")
                when (element.align) {
                    "top" -> appendLine("$v.topAlignContent();")
                    "bottom" -> appendLine("$v.bottomAlignContent();")
                    else -> appendLine("$v.centerAlignContent();")
                }
                if (element.spacing > 0) appendLine("$v.spacing = ${num(element.spacing)};")
                element.padding?.let { appendLine("$v.setPadding(${num(it)}, ${num(it)}, ${num(it)}, ${num(it)});") }
                element.bg?.let { appendLine("$v.backgroundColor = ${color(it)};") }
                if (element.cornerRadius > 0) appendLine("$v.cornerRadius = ${num(element.cornerRadius)};")
                element.children.forEach { element(it, v, names, bindings) }
            }
        }
    }

    private fun StringBuilder.align(v: String, align: String) {
        when (align) {
            "center" -> appendLine("$v.centerAlignText();")
            "right" -> appendLine("$v.rightAlignText();")
        }
    }

    private fun loader(binding: DataBinding): String = when (binding) {
        is DataBinding.DateText -> "formatDate(new Date(), ${str(binding.format)})"
        DataBinding.Battery -> "Math.round(Device.batteryLevel() * 100)"
        is DataBinding.Parameter -> "args.widgetParameter || ${str(binding.fallback)}"
        is DataBinding.Json -> "pick(await new Request(${str(binding.url)}).loadJSON(), ${str(binding.path)})"
        is DataBinding.Weather -> when (binding.field) {
            DataBinding.Weather.Field.Temperature -> "Math.round((await weather()).temperature_2m)"
            DataBinding.Weather.Field.Condition -> "weatherLabel((await weather()).weather_code)"
        }
        is DataBinding.NextEvent -> when (binding.field) {
            DataBinding.NextEvent.Field.Title -> "(await nextEvent())?.title ?? ${str(texts.noEvents)}"
            DataBinding.NextEvent.Field.Time -> "eventTime(await nextEvent())"
        }
        is DataBinding.Countdown -> when (binding.field) {
            DataBinding.Countdown.Field.Days -> "countdown(${str(binding.date)}, ${binding.yearly})"
            DataBinding.Countdown.Field.Name -> "countdownName(${str(binding.date)})"
        }
        is DataBinding.Progress -> "progress(${str(binding.period.name.lowercase())})"
        is DataBinding.WorldTime -> "worldTime(${str(binding.zone)})"
        is DataBinding.Reminders -> when (binding.field) {
            DataBinding.Reminders.Field.Count -> "(await openReminders()).length"
            DataBinding.Reminders.Field.Next -> "(await openReminders())[0]?.title ?? ${str(texts.noReminders)}"
        }
    }

    /** Unique variable names: text1, text2, stack1 ... */
    private class Names {
        private val counts = mutableMapOf<String, Int>()
        fun next(prefix: String): String = "$prefix${counts.merge(prefix, 1, Int::plus)}"
    }

    companion object {
        private const val VALUE = "{value}"
        private const val PLACEHOLDER = "SFSymbol.named(\"photo\").image"

        private val DATE_STYLES = mapOf(
            "time" to "applyTimeStyle",
            "date" to "applyDateStyle",
            "relative" to "applyRelativeStyle",
            "timer" to "applyTimerStyle",
        )

        private val WEIGHTS = setOf("ultraLight", "thin", "light", "medium", "semibold", "bold", "heavy", "black")

        /** A JavaScript string literal. */
        fun str(text: String): String = buildString {
            append('"')
            text.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '<' -> append("\\u003c")
                    ' ' -> append("\\u2028")
                    ' ' -> append("\\u2029")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }

        private fun num(value: Float): String =
            if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()

        /** [name]: null = system, "monospace", or a font name ("serif", a font file in Documents/fonts). */
        private fun font(weight: String, size: Float, name: String? = null): String = when {
            name == null -> if (weight in WEIGHTS) "Font.${weight}SystemFont(${num(size)})" else "Font.systemFont(${num(size)})"
            name == "monospace" -> "Font.${if (weight in WEIGHTS) weight else "regular"}MonospacedSystemFont(${num(size)})"
            weight in WEIGHTS -> "Object.assign(new Font(${str(name)}, ${num(size)}), { weight: ${str(weight)} })"
            else -> "new Font(${str(name)}, ${num(size)})"
        }

        /** `#AARRGGBB` → `new Color("#RRGGBB", alpha)`, as Scriptable expects. */
        private fun color(argb: String): String {
            val hex = argb.removePrefix("#")
            if (hex.length != 8) return "new Color(${str("#$hex")})"
            val alpha = hex.substring(0, 2).toInt(16) / 255f
            val rgb = str("#" + hex.substring(2))
            return if (alpha >= 1f) "new Color($rgb)" else "new Color($rgb, ${"%.3f".format(java.util.Locale.ROOT, alpha).trimEnd('0').trimEnd('.')})"
        }

        private fun bookmarkHelper(texts: GeneratorTexts) = """
            |// ${texts.bookmarkComment}
            |function bookmarkImage(name) {
            |  const fm = FileManager.local();
            |  try { return fm.bookmarkExists(name) ? fm.readImage(fm.bookmarkedPath(name)) : null; } catch (e) { return null; }
            |}
            |
        """.trimMargin() + "\n"

        /** Helpers for [bindings] only: a design that shows the date has no weather or calendar code. */
        private fun dataHelpers(texts: GeneratorTexts, bindings: List<DataBinding>): String = buildString {
            append(commonHelpers(texts))
            if (bindings.any { it is DataBinding.Json }) append(pickHelper)
            if (bindings.any { it is DataBinding.Weather }) append(weatherHelpers(texts))
            if (bindings.any { it is DataBinding.NextEvent }) append(eventHelpers(texts))
            if (bindings.any { it is DataBinding.Reminders }) append(reminderHelpers)
            if (bindings.any { it is DataBinding.Countdown }) append(countdownHelpers)
            if (bindings.any { it is DataBinding.Progress }) append(progressHelper)
            if (bindings.any { it is DataBinding.WorldTime }) append(worldTimeHelper)
            appendLine()
        }

        private fun commonHelpers(texts: GeneratorTexts) = """
            |// ${texts.dataComment}
            |const data = {};
            |async function load(key, read) {
            |  try { data[key] = await read(); } catch (e) { console.warn(`${'$'}{key}: ${'$'}{e}`); data[key] = "–"; }
            |  if (data[key] === undefined || data[key] === null) data[key] = "–";
            |}
            |function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }
        """.trimMargin() + "\n"

        private val pickHelper = """
            |function pick(json, path) {
            |  const value = path.split(".").filter(Boolean).reduce((o, k) => (o == null ? undefined : o[k]), json);
            |  return value !== null && typeof value === "object" ? JSON.stringify(value) : value;
            |}
        """.trimMargin() + "\n"

        private fun weatherHelpers(texts: GeneratorTexts) = """
            |let _weather;
            |async function weather() {
            |  if (_weather) return _weather;
            |  let loc = { latitude: 37.5665, longitude: 126.978 };
            |  try { Location.setAccuracyToKilometer(); loc = await Location.current(); } catch (e) {}
            |  const url = `https://api.open-meteo.com/v1/forecast?latitude=${'$'}{loc.latitude}&longitude=${'$'}{loc.longitude}&current=temperature_2m,weather_code&timezone=auto`;
            |  return (_weather = (await new Request(url).loadJSON()).current);
            |}
            |function weatherLabel(code) {
            |  const labels = [[[0], ${str(texts.weather[0])}], [[1, 2], ${str(texts.weather[1])}], [[3], ${str(texts.weather[2])}], [[45, 48], ${str(texts.weather[3])}],
            |    [[51, 53, 55, 56, 57], ${str(texts.weather[4])}], [[61, 63, 65, 66, 67, 80, 81, 82], ${str(texts.weather[5])}],
            |    [[71, 73, 75, 77, 85, 86], ${str(texts.weather[6])}], [[95, 96, 99], ${str(texts.weather[7])}]];
            |  return (labels.find(([codes]) => codes.includes(code)) ?? [null, "–"])[1];
            |}
        """.trimMargin() + "\n"

        private fun eventHelpers(texts: GeneratorTexts) = """
            |let _event;
            |async function nextEvent() {
            |  if (_event !== undefined) return _event;
            |  const now = new Date();
            |  const events = (await CalendarEvent.today([])).filter((e) => e.isAllDay || e.endDate > now);
            |  events.sort((a, b) => a.startDate - b.startDate);
            |  return (_event = events[0] ?? null);
            |}
            |function eventTime(e) {
            |  if (!e) return "";
            |  return e.isAllDay ? ${str(texts.allDay)} : `${'$'}{formatDate(e.startDate, "HH:mm")} – ${'$'}{formatDate(e.endDate, "HH:mm")}`;
            |}
        """.trimMargin() + "\n"

        private val reminderHelpers = """
            |let _reminders;
            |async function openReminders() {
            |  if (_reminders) return _reminders;
            |  const list = await Reminder.allIncomplete();
            |  list.sort((a, b) => (a.dueDate ? a.dueDate.getTime() : Infinity) - (b.dueDate ? b.dueDate.getTime() : Infinity));
            |  return (_reminders = list);
            |}
        """.trimMargin() + "\n"

        private val countdownHelpers = """
            |function countdownTarget(date) {
            |  const m = (date || (args.widgetParameter || "")).trim().match(/^(\d{4})-(\d{1,2})-(\d{1,2})\s*(.*)$/);
            |  return m ? { date: new Date(+m[1], +m[2] - 1, +m[3]), name: m[4] } : null;
            |}
            |function countdown(date, yearly) {
            |  const target = countdownTarget(date);
            |  if (!target) return null;
            |  const today = new Date();
            |  today.setHours(0, 0, 0, 0);
            |  let day = target.date;
            |  while (yearly && day < today) day = new Date(day.getFullYear() + 1, day.getMonth(), day.getDate());
            |  const days = Math.round((day - today) / 86400000);
            |  return days === 0 ? "D-DAY" : days > 0 ? `D-${'$'}{days}` : `D+${'$'}{-days}`;
            |}
            |function countdownName(date) { const target = countdownTarget(date); return target ? target.name : null; }
        """.trimMargin() + "\n"

        private val progressHelper = """
            |function progress(period) {
            |  const now = new Date();
            |  const y = now.getFullYear(), m = now.getMonth(), d = now.getDate();
            |  const monday = d - ((now.getDay() + 6) % 7);
            |  const [start, end] = {
            |    day: [new Date(y, m, d), new Date(y, m, d + 1)],
            |    week: [new Date(y, m, monday), new Date(y, m, monday + 7)],
            |    month: [new Date(y, m, 1), new Date(y, m + 1, 1)],
            |    year: [new Date(y, 0, 1), new Date(y + 1, 0, 1)],
            |  }[period];
            |  return Math.floor(((now - start) / (end - start)) * 100);
            |}
        """.trimMargin() + "\n"

        private val worldTimeHelper = """
            |function worldTime(zone) {
            |  return new Intl.DateTimeFormat("en-GB", { timeZone: zone, hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date());
            |}
        """.trimMargin() + "\n"
    }
}

/** Words the generated script contains, per language (chosen when the design is saved). */
data class GeneratorTexts(
    val header: List<String>,
    val dataComment: String,
    val noEvents: String,
    val noReminders: String,
    val allDay: String,
    val bookmarkComment: String,
    /** Clear, partly cloudy, cloudy, fog, drizzle, rain, snow, thunderstorm. */
    val weather: List<String>,
) {
    companion object {
        val KOREAN = GeneratorTexts(
            header = listOf("디자이너로 만든 위젯입니다. 디자이너에서 열어 고치세요.", "여기서 코드를 직접 고치면 디자이너에서 다시 열 때 고친 내용이 사라집니다."),
            dataComment = "데이터 블록: 실패하면 \"–\"를 보여 줍니다.",
            noEvents = "일정 없음",
            noReminders = "할 일 없음",
            allDay = "하루 종일",
            bookmarkComment = "사진은 파일 북마크에서 읽습니다(설정 → 파일 북마크). 없으면 빈 자리로 둡니다.",
            weather = listOf("맑음", "구름 조금", "흐림", "안개", "이슬비", "비", "눈", "뇌우"),
        )
        val ENGLISH = GeneratorTexts(
            header = listOf("Made in the widget designer. Open it there to change it.", "Edits made to this code are lost when the designer saves again."),
            dataComment = "Data blocks: show \"–\" when a value can't be loaded.",
            noEvents = "No events",
            noReminders = "Nothing to do",
            allDay = "All day",
            bookmarkComment = "Pictures come from file bookmarks (Settings → File bookmarks); a missing one leaves a placeholder.",
            weather = listOf("Clear", "Partly cloudy", "Cloudy", "Fog", "Drizzle", "Rain", "Snow", "Thunderstorm"),
        )
    }
}
