// 할 일: 미완료 할 일을 보여주고, 위젯에서 ○ 를 누르면 완료 처리합니다.
// 위젯 파라미터에 목록 이름을 쓰면 그 목록만 보여줍니다. 예) "장보기"
// 앱에서 실행하면 새 할 일을 추가할 수 있습니다.
const BG = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
const FG = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
const SUB = Color.dynamic(new Color("#6E6E73"), new Color("#98989D"));
const ACCENT = new Color("#0A84FF");
const family = config.widgetFamily ?? "medium";
const max = { small: 3, medium: 4, large: 10 }[family] ?? 4;

async function lists() {
  if (!args.widgetParameter) return [];
  return (await Calendar.forReminders()).filter((c) => c.title === args.widgetParameter.trim());
}

// 위젯에서 탭한 할 일 완료 처리
if (args.widgetAction?.startsWith("done:")) {
  const id = args.widgetAction.slice(5);
  const target = (await Reminder.allIncomplete()).find((r) => r.identifier === id);
  if (target) {
    target.isCompleted = true;
    target.save();
  }
}

if (config.runsInApp) {
  const a = new Alert();
  a.title = "새 할 일";
  a.addTextField("할 일", "");
  a.addAction("추가");
  a.addCancelAction("취소");
  if ((await a.present()) === 0 && a.textFieldValue().trim()) {
    const r = new Reminder();
    r.title = a.textFieldValue().trim();
    const target = (await lists())[0];
    if (target) r.calendar = target;
    r.save();
  }
}

const items = (await Reminder.allIncomplete(await lists()))
  .sort((a, b) => (a.dueDate?.getTime() ?? Infinity) - (b.dueDate?.getTime() ?? Infinity));

const w = new ListWidget();
w.backgroundColor = BG;
w.setPadding(12, 14, 12, 14);

const head = w.addStack();
head.centerAlignContent();
const title = head.addText(args.widgetParameter?.trim() || "할 일");
title.font = Font.boldSystemFont(15);
title.textColor = ACCENT;
head.addSpacer();
const count = head.addText(String(items.length));
count.font = Font.boldSystemFont(15);
count.textColor = FG;
w.addSpacer(8);

if (items.length === 0) {
  const t = w.addText("모두 끝냈어요 ✓");
  t.font = Font.systemFont(13);
  t.textColor = SUB;
}
for (const r of items.slice(0, max)) {
  const row = w.addStack();
  row.centerAlignContent();
  row.addButton("○", `done:${r.identifier}`);
  row.addSpacer(6);
  const col = row.addStack();
  col.layoutVertically();
  const t = col.addText(r.title);
  t.font = Font.systemFont(13);
  t.textColor = FG;
  t.lineLimit = 1;
  if (r.dueDate && family !== "small") {
    const due = col.addText(r.dueDate.toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "numeric", minute: "2-digit" }));
    due.font = Font.systemFont(10);
    due.textColor = r.isOverdue ? new Color("#FF3B30") : SUB;
  }
  w.addSpacer(4);
}
w.addSpacer();

if (config.runsInWidget) Script.setWidget(w);
else await w.presentMedium();
Script.complete();
