// scriptwidget-design: {"refreshMinutes":30,"children":[{"type":"text","text":"할 일","color":"#FF8E8E93","size":13.0},{"type":"text","text":"{value}개 남음","data":{"type":"reminders"},"size":24.0,"weight":"bold"},{"type":"text","text":"{value}","data":{"type":"reminders","field":"Next"},"size":14.0,"lineLimit":2}]}
// scriptwidget-design-hash: dd3a3a22ffd208d3
// 디자이너로 만든 위젯입니다. 디자이너에서 열어 고치세요.
// 여기서 코드를 직접 고치면 디자이너에서 다시 열 때 고친 내용이 사라집니다.

// 데이터 블록: 실패하면 "–"를 보여 줍니다.
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
await load("d1", async () => (await openReminders())[0]?.title ?? "할 일 없음");

const w = new ListWidget();
w.refreshAfterDate = new Date(Date.now() + 30 * 60 * 1000);

const text1 = w.addText("할 일");
text1.font = Font.systemFont(13);
text1.textColor = new Color("#8E8E93");

const text2 = w.addText("" + String(data.d0) + "개 남음");
text2.font = Font.boldSystemFont(24);

const text3 = w.addText("" + String(data.d1) + "");
text3.font = Font.systemFont(14);
text3.lineLimit = 2;

Script.setWidget(w);
if (!config.runsInWidget) await w.presentMedium();
Script.complete();
