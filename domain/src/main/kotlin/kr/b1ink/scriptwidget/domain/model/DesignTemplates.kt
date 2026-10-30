package kr.b1ink.scriptwidget.domain.model

/** Starting points offered when a new design is created. */
enum class DesignTemplates(val title: String, val design: WidgetDesign) {
    Blank("빈 위젯", WidgetDesign()),
    Clock(
        "시계",
        WidgetDesign(
            children = listOf(
                DesignText("{value}", data = DataBinding.DateText("EEEE"), color = GRAY, size = 13f),
                DesignDate(style = "time", size = 40f, weight = "bold"),
                DesignText("{value}", data = DataBinding.DateText("M월 d일"), size = 15f),
            ),
        ),
    ),
    Battery(
        "배터리",
        WidgetDesign(
            refreshMinutes = 15,
            children = listOf(
                DesignText("배터리", color = GRAY, size = 13f),
                DesignStack(
                    dir = "h", spacing = 6f,
                    children = listOf(
                        DesignSymbol("battery.100", color = "#FF34C759", size = 26f),
                        DesignText("{value}%", data = DataBinding.Battery, size = 30f, weight = "bold"),
                    ),
                ),
            ),
        ),
    ),
    NextEvent(
        "다음 일정",
        WidgetDesign(
            refreshMinutes = 30,
            children = listOf(
                DesignText("다음 일정", color = GRAY, size = 13f),
                DesignText("{value}", data = DataBinding.NextEvent(DataBinding.NextEvent.Field.Title), size = 17f, weight = "semibold", lineLimit = 2),
                DesignText("{value}", data = DataBinding.NextEvent(DataBinding.NextEvent.Field.Time), color = "#FF0A84FF", size = 14f),
            ),
        ),
    ),
    Reminders(
        "할 일",
        WidgetDesign(
            refreshMinutes = 30,
            children = listOf(
                DesignText("할 일", color = GRAY, size = 13f),
                DesignText("{value}개 남음", data = DataBinding.Reminders(DataBinding.Reminders.Field.Count), size = 24f, weight = "bold"),
                DesignText("{value}", data = DataBinding.Reminders(DataBinding.Reminders.Field.Next), size = 14f, lineLimit = 2),
            ),
        ),
    ),
    Weather(
        "날씨",
        WidgetDesign(
            refreshMinutes = 60,
            background = DesignBackground(gradient = listOf("#FF2F80ED", "#FF56CCF2")),
            children = listOf(
                DesignText("{value}", data = DataBinding.DateText("M월 d일 EEEE"), size = 13f),
                DesignText("{value}°", data = DataBinding.Weather(DataBinding.Weather.Field.Temperature), size = 40f, weight = "bold"),
                DesignText("{value}", data = DataBinding.Weather(DataBinding.Weather.Field.Condition), size = 15f),
            ),
        ),
    ),
    GitHubStars(
        "JSON 값 (GitHub 별)",
        WidgetDesign(
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
        ),
    ),
    ;

    companion object {
        val all: List<DesignTemplates> = entries
    }
}

private const val GRAY = "#FF8E8E93"
