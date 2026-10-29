// 오늘 일정: 오늘 남은 일정을 시간순으로 보여줍니다. (캘린더 권한 필요)
// 위젯 파라미터에 캘린더 이름을 쓰면 그 캘린더만 보여줍니다. 예) "Work"
const BG = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
const FG = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
const SUB = Color.dynamic(new Color("#6E6E73"), new Color("#98989D"));
const ACCENT = new Color("#FF3B30");
const family = config.widgetFamily ?? "medium";
const max = { small: 3, medium: 4, large: 9 }[family] ?? 4;

const w = new ListWidget();
w.backgroundColor = BG;
w.setPadding(12, 14, 12, 14);
w.url = "content://com.android.calendar/time/";

const now = new Date();
const head = w.addStack();
head.centerAlignContent();
const day = head.addText(now.toLocaleDateString("ko-KR", { weekday: "short" }));
day.font = Font.semiboldSystemFont(12);
day.textColor = ACCENT;
head.addSpacer(6);
const date = head.addText(`${now.getMonth() + 1}월 ${now.getDate()}일`);
date.font = Font.boldSystemFont(family === "small" ? 15 : 17);
date.textColor = FG;
w.addSpacer(8);

let events = [];
let problem = null;
try {
  let calendars = [];
  if (args.widgetParameter) {
    calendars = (await Calendar.forEvents()).filter((c) => c.title === args.widgetParameter.trim());
  }
  events = (await CalendarEvent.today(calendars))
    .filter((e) => e.isAllDay || e.endDate > now)
    .sort((a, b) => (a.isAllDay === b.isAllDay ? a.startDate - b.startDate : a.isAllDay ? -1 : 1));
} catch (e) {
  problem = "캘린더 권한을 허용해 주세요";
}

const time = (d) => d.toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" });

if (problem || events.length === 0) {
  const t = w.addText(problem ?? "오늘 남은 일정이 없어요 🎉");
  t.font = Font.systemFont(13);
  t.textColor = SUB;
} else {
  for (const e of events.slice(0, max)) {
    const row = w.addStack();
    row.centerAlignContent();
    const bar = row.addStack();
    bar.size = new Size(3, family === "small" ? 26 : 30);
    bar.cornerRadius = 1.5;
    bar.backgroundColor = e.calendar?.color ?? ACCENT;
    row.addSpacer(8);
    const col = row.addStack();
    col.layoutVertically();
    const title = col.addText(e.title || "(제목 없음)");
    title.font = Font.semiboldSystemFont(13);
    title.textColor = FG;
    title.lineLimit = 1;
    const when = col.addText(e.isAllDay ? "하루 종일" : `${time(e.startDate)} – ${time(e.endDate)}`);
    when.font = Font.systemFont(11);
    when.textColor = SUB;
    w.addSpacer(6);
  }
  if (events.length > max) {
    const more = w.addText(`외 ${events.length - max}개`);
    more.font = Font.systemFont(11);
    more.textColor = SUB;
  }
}
w.addSpacer();
w.refreshAfterDate = new Date(Date.now() + 30 * 60 * 1000);

if (config.runsInWidget) Script.setWidget(w);
else await w.presentMedium();
Script.complete();
