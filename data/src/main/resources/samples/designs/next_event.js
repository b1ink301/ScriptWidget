// scriptwidget-design: {"refreshMinutes":30,"children":[{"type":"text","text":"다음 일정","color":"#FF8E8E93","size":13.0},{"type":"text","text":"{value}","data":{"type":"nextEvent"},"weight":"semibold","lineLimit":2},{"type":"text","text":"{value}","data":{"type":"nextEvent","field":"Time"},"color":"#FF0A84FF","size":14.0}]}
// scriptwidget-design-hash: 21880d5074f833d2
// 디자이너로 만든 위젯입니다. 디자이너에서 열어 고치세요.
// 여기서 코드를 직접 고치면 디자이너에서 다시 열 때 고친 내용이 사라집니다.

// 데이터 블록: 실패하면 "–"를 보여 줍니다.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }
let _event;
async function nextEvent() {
  if (_event !== undefined) return _event;
  const now = new Date();
  const events = (await CalendarEvent.today([])).filter((e) => e.isAllDay || e.endDate > now);
  events.sort((a, b) => a.startDate - b.startDate);
  return (_event = events[0] ?? null);
}
function eventTime(e) {
  if (!e) return "";
  return e.isAllDay ? "하루 종일" : `${formatDate(e.startDate, "HH:mm")} – ${formatDate(e.endDate, "HH:mm")}`;
}

await load("d0", async () => (await nextEvent())?.title ?? "일정 없음");
await load("d1", async () => eventTime(await nextEvent()));

const w = new ListWidget();
w.refreshAfterDate = new Date(Date.now() + 30 * 60 * 1000);

const text1 = w.addText("다음 일정");
text1.font = Font.systemFont(13);
text1.textColor = new Color("#8E8E93");

const text2 = w.addText("" + String(data.d0) + "");
text2.font = Font.semiboldSystemFont(17);
text2.lineLimit = 2;

const text3 = w.addText("" + String(data.d1) + "");
text3.font = Font.systemFont(14);
text3.textColor = new Color("#0A84FF");

Script.setWidget(w);
if (!config.runsInWidget) await w.presentMedium();
Script.complete();
