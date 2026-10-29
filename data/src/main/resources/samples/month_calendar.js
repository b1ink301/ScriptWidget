// 이번 달 달력: 오늘을 강조하고, 일정이 있는 날에 점을 찍습니다. (캘린더 권한이 없으면 점 없이)
const BG = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
const FG = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
const SUB = Color.dynamic(new Color("#AEAEB2"), new Color("#636366"));
const RED = new Color("#FF3B30");
const BLUE = new Color("#0A84FF");
const family = config.widgetFamily ?? "medium";
const size = { small: 10, medium: 11, large: 15 }[family] ?? 11;

const now = new Date();
const year = now.getFullYear();
const month = now.getMonth();
const first = new Date(year, month, 1);
const days = new Date(year, month + 1, 0).getDate();

let busy = new Set();
try {
  const events = await CalendarEvent.between(first, new Date(year, month + 1, 1));
  for (const e of events) busy.add(e.startDate.getDate());
} catch (e) {}

const w = new ListWidget();
w.backgroundColor = BG;
w.setPadding(10, 12, 10, 12);
w.url = "content://com.android.calendar/time/";

const title = w.addText(`${year}년 ${month + 1}월`);
title.font = Font.boldSystemFont(size + 2);
title.textColor = RED;
w.addSpacer(4);

function cell(row, text, color, bold, dot) {
  const c = row.addStack();
  c.layoutVertically();
  const line = c.addStack();
  line.addSpacer();
  const t = line.addText(text);
  t.font = bold ? Font.boldSystemFont(size) : Font.systemFont(size);
  t.textColor = color;
  t.lineLimit = 1;
  t.minimumScaleFactor = 0.6;
  line.addSpacer();
  const mark = c.addStack();
  mark.addSpacer();
  const d = mark.addText(dot ? "•" : " ");
  d.font = Font.systemFont(Math.max(6, size - 4));
  d.textColor = dot ? BLUE : SUB;
  mark.addSpacer();
}

const head = w.addStack();
["일", "월", "화", "수", "목", "금", "토"].forEach((d, i) => cell(head, d, i === 0 ? RED : i === 6 ? BLUE : SUB, true, false));

let day = 1 - first.getDay();
while (day <= days) {
  const row = w.addStack();
  for (let i = 0; i < 7; i++, day++) {
    const inMonth = day >= 1 && day <= days;
    const today = inMonth && day === now.getDate();
    const color = today ? RED : !inMonth ? SUB : i === 0 ? RED : i === 6 ? BLUE : FG;
    cell(row, inMonth ? String(day) : "", color, today, inMonth && busy.has(day));
  }
}

const tomorrow = new Date(year, month, now.getDate() + 1);
w.refreshAfterDate = tomorrow;

if (config.runsInWidget) Script.setWidget(w);
else await w.presentLarge();
Script.complete();
