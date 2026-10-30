package kr.b1ink.scriptwidget.domain.model

/** The words and date patterns a template puts into a new design, per language. */
data class TemplateTexts(
    val titles: Map<DesignTemplates, String>,
    val battery: String,
    val nextEvent: String,
    val reminders: String,
    /** `{value}` is the number of open reminders. */
    val remindersLeft: String,
    val clockDate: String,
    val longDate: String,
) {
    companion object {
        val KOREAN = TemplateTexts(
            titles = mapOf(
                DesignTemplates.Blank to "빈 위젯", DesignTemplates.Clock to "시계", DesignTemplates.Battery to "배터리",
                DesignTemplates.NextEvent to "다음 일정", DesignTemplates.Reminders to "할 일", DesignTemplates.Weather to "날씨",
                DesignTemplates.GitHubStars to "JSON 값 (GitHub 별)",
            ),
            battery = "배터리",
            nextEvent = "다음 일정",
            reminders = "할 일",
            remindersLeft = "{value}개 남음",
            clockDate = "M월 d일",
            longDate = "M월 d일 EEEE",
        )
        val ENGLISH = TemplateTexts(
            titles = mapOf(
                DesignTemplates.Blank to "Blank widget", DesignTemplates.Clock to "Clock", DesignTemplates.Battery to "Battery",
                DesignTemplates.NextEvent to "Next event", DesignTemplates.Reminders to "Reminders", DesignTemplates.Weather to "Weather",
                DesignTemplates.GitHubStars to "JSON value (GitHub stars)",
            ),
            battery = "Battery",
            nextEvent = "Next event",
            reminders = "Reminders",
            remindersLeft = "{value} left",
            clockDate = "MMM d",
            longDate = "EEEE, MMM d",
        )
    }
}

/** Starting points offered when a new design is created. */
enum class DesignTemplates {
    Blank,
    Clock,
    Battery,
    NextEvent,
    Reminders,
    Weather,
    GitHubStars,
    ;

    /** The Korean title; the app shows its own translation. */
    val title: String get() = TemplateTexts.KOREAN.titles.getValue(this)

    /** The template in Korean, the app's original language. */
    val design: WidgetDesign get() = design(TemplateTexts.KOREAN)

    fun design(texts: TemplateTexts): WidgetDesign = when (this) {
        Blank -> WidgetDesign()
        Clock -> WidgetDesign(
            children = listOf(
                DesignText("{value}", data = DataBinding.DateText("EEEE"), color = GRAY, size = 13f),
                DesignDate(style = "time", size = 40f, weight = "bold"),
                DesignText("{value}", data = DataBinding.DateText(texts.clockDate), size = 15f),
            ),
        )
        Battery -> WidgetDesign(
            refreshMinutes = 15,
            children = listOf(
                DesignText(texts.battery, color = GRAY, size = 13f),
                DesignStack(
                    dir = "h",
                    spacing = 6f,
                    children = listOf(
                        DesignSymbol("battery.100", color = "#FF34C759", size = 26f),
                        DesignText("{value}%", data = DataBinding.Battery, size = 30f, weight = "bold"),
                    ),
                ),
            ),
        )
        NextEvent -> WidgetDesign(
            refreshMinutes = 30,
            children = listOf(
                DesignText(texts.nextEvent, color = GRAY, size = 13f),
                DesignText("{value}", data = DataBinding.NextEvent(DataBinding.NextEvent.Field.Title), size = 17f, weight = "semibold", lineLimit = 2),
                DesignText("{value}", data = DataBinding.NextEvent(DataBinding.NextEvent.Field.Time), color = "#FF0A84FF", size = 14f),
            ),
        )
        Reminders -> WidgetDesign(
            refreshMinutes = 30,
            children = listOf(
                DesignText(texts.reminders, color = GRAY, size = 13f),
                DesignText(texts.remindersLeft, data = DataBinding.Reminders(DataBinding.Reminders.Field.Count), size = 24f, weight = "bold"),
                DesignText("{value}", data = DataBinding.Reminders(DataBinding.Reminders.Field.Next), size = 14f, lineLimit = 2),
            ),
        )
        Weather -> WidgetDesign(
            refreshMinutes = 60,
            background = DesignBackground(gradient = listOf("#FF2F80ED", "#FF56CCF2")),
            children = listOf(
                DesignText("{value}", data = DataBinding.DateText(texts.longDate), size = 13f),
                DesignText("{value}°", data = DataBinding.Weather(DataBinding.Weather.Field.Temperature), size = 40f, weight = "bold"),
                DesignText("{value}", data = DataBinding.Weather(DataBinding.Weather.Field.Condition), size = 15f),
            ),
        )
        GitHubStars -> WidgetDesign(
            refreshMinutes = 60,
            url = "https://github.com/b1ink301/ScriptWidget",
            children = listOf(
                DesignText("ScriptWidget", color = GRAY, size = 13f),
                DesignText(
                    "⭐ {value}",
                    data = DataBinding.Json("https://api.github.com/repos/b1ink301/ScriptWidget", "stargazers_count"),
                    size = 30f,
                    weight = "bold",
                ),
            ),
        )
    }

    companion object {
        val all: List<DesignTemplates> = entries
    }
}

private const val GRAY = "#FF8E8E93"
