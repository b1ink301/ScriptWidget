// scriptwidget-design: {"background":{"gradient":["#FFB31217","#FFE52D27"]},"refreshMinutes":60,"children":[{"type":"text","text":"Christmas","color":"#FFFFFFFF","size":15.0,"weight":"semibold"},{"type":"text","text":"{value}","data":{"type":"countdown","date":"2026-12-25","yearly":true},"color":"#FFFFFFFF","size":40.0,"weight":"bold"}]}
// scriptwidget-design-hash: 18475f2d3b8ad9e1
// Made in the widget designer. Open it there to change it.
// Edits made to this code are lost when the designer saves again.

// Data blocks: show "–" when a value can't be loaded.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }
function countdownTarget(date) {
  const m = (date || (args.widgetParameter || "")).trim().match(/^(\d{4})-(\d{1,2})-(\d{1,2})\s*(.*)$/);
  return m ? { date: new Date(+m[1], +m[2] - 1, +m[3]), name: m[4] } : null;
}
function countdown(date, yearly) {
  const target = countdownTarget(date);
  if (!target) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  let day = target.date;
  while (yearly && day < today) day = new Date(day.getFullYear() + 1, day.getMonth(), day.getDate());
  const days = Math.round((day - today) / 86400000);
  return days === 0 ? "D-DAY" : days > 0 ? `D-${days}` : `D+${-days}`;
}
function countdownName(date) { const target = countdownTarget(date); return target ? target.name : null; }

await load("d0", async () => countdown("2026-12-25", true));

const w = new ListWidget();
const bg = new LinearGradient();
bg.colors = [new Color("#B31217"), new Color("#E52D27")];
bg.locations = [0, 1];
w.backgroundGradient = bg;
w.refreshAfterDate = new Date(Date.now() + 60 * 60 * 1000);

const text1 = w.addText("Christmas");
text1.font = Font.semiboldSystemFont(15);
text1.textColor = new Color("#FFFFFF");

const text2 = w.addText("" + String(data.d0) + "");
text2.font = Font.boldSystemFont(40);
text2.textColor = new Color("#FFFFFF");

Script.setWidget(w);
if (!config.runsInWidget) await w.presentMedium();
Script.complete();
