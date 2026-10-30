// scriptwidget-design: {"children":[{"type":"text","text":"{value}","data":{"type":"dateText","format":"EEEE"},"color":"#FF8E8E93","size":13.0},{"type":"date","size":34.0,"weight":"bold"},{"type":"text","text":"{value}","data":{"type":"dateText","format":"MMM d"},"size":15.0}]}
// scriptwidget-design-hash: 50b592506bcb93a6
// Made in the widget designer. Open it there to change it.
// Edits made to this code are lost when the designer saves again.

// Data blocks: show "–" when a value can't be loaded.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }

await load("d0", async () => formatDate(new Date(), "EEEE"));
await load("d1", async () => formatDate(new Date(), "MMM d"));

const w = new ListWidget();

const text1 = w.addText("" + String(data.d0) + "");
text1.font = Font.systemFont(13);
text1.textColor = new Color("#8E8E93");

const date1 = w.addDate(new Date());
date1.applyTimeStyle();
date1.font = Font.boldSystemFont(34);

const text2 = w.addText("" + String(data.d1) + "");
text2.font = Font.systemFont(15);

Script.setWidget(w);
if (!config.runsInWidget) await w.presentMedium();
Script.complete();
