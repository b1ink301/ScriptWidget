// scriptwidget-design: {"refreshMinutes":30,"children":[{"type":"text","text":"Next event","color":"#FF8E8E93","size":13.0},{"type":"text","text":"{value}","data":{"type":"nextEvent"},"weight":"semibold","lineLimit":2},{"type":"text","text":"{value}","data":{"type":"nextEvent","field":"Time"},"color":"#FF0A84FF","size":14.0}]}
// scriptwidget-design-hash: ff4a72579bbfc6c7
// Made in the widget designer. Open it there to change it.
// Edits made to this code are lost when the designer saves again.

// Data blocks: show "–" when a value can't be loaded.
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
  return e.isAllDay ? "All day" : `${formatDate(e.startDate, "HH:mm")} – ${formatDate(e.endDate, "HH:mm")}`;
}

await load("d0", async () => (await nextEvent())?.title ?? "No events");
await load("d1", async () => eventTime(await nextEvent()));

const w = new ListWidget();
w.refreshAfterDate = new Date(Date.now() + 30 * 60 * 1000);

const text1 = w.addText("Next event");
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
