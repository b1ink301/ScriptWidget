// scriptwidget-design: {"refreshMinutes":30,"children":[{"type":"text","text":"Reminders","color":"#FF8E8E93","size":13.0},{"type":"text","text":"{value} left","data":{"type":"reminders"},"size":24.0,"weight":"bold"},{"type":"text","text":"{value}","data":{"type":"reminders","field":"Next"},"size":14.0,"lineLimit":2}]}
// scriptwidget-design-hash: fae6b542fc3dbee1
// Made in the widget designer. Open it there to change it.
// Edits made to this code are lost when the designer saves again.

// Data blocks: show "–" when a value can't be loaded.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }
let _reminders;
async function openReminders() {
  if (_reminders) return _reminders;
  const list = await Reminder.allIncomplete();
  list.sort((a, b) => (a.dueDate ? a.dueDate.getTime() : Infinity) - (b.dueDate ? b.dueDate.getTime() : Infinity));
  return (_reminders = list);
}

await load("d0", async () => (await openReminders()).length);
await load("d1", async () => (await openReminders())[0]?.title ?? "Nothing to do");

const w = new ListWidget();
w.refreshAfterDate = new Date(Date.now() + 30 * 60 * 1000);

const text1 = w.addText("Reminders");
text1.font = Font.systemFont(13);
text1.textColor = new Color("#8E8E93");

const text2 = w.addText("" + String(data.d0) + " left");
text2.font = Font.boldSystemFont(24);

const text3 = w.addText("" + String(data.d1) + "");
text3.font = Font.systemFont(14);
text3.lineLimit = 2;

Script.setWidget(w);
if (!config.runsInWidget) await w.presentMedium();
Script.complete();
