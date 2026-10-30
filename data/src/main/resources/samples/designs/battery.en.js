// scriptwidget-design: {"refreshMinutes":15,"children":[{"type":"text","text":"Battery","color":"#FF8E8E93","size":13.0},{"type":"stack","spacing":6.0,"children":[{"type":"symbol","name":"battery.100","color":"#FF34C759","size":26.0},{"type":"text","text":"{value}%","data":{"type":"battery"},"size":30.0,"weight":"bold"}]}]}
// scriptwidget-design-hash: c5399f34ac11fb85
// Made in the widget designer. Open it there to change it.
// Edits made to this code are lost when the designer saves again.

// Data blocks: show "–" when a value can't be loaded.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }

await load("d0", async () => Math.round(Device.batteryLevel() * 100));

const w = new ListWidget();
w.refreshAfterDate = new Date(Date.now() + 15 * 60 * 1000);

const text1 = w.addText("Battery");
text1.font = Font.systemFont(13);
text1.textColor = new Color("#8E8E93");

const stack1 = w.addStack();
stack1.layoutHorizontally();
stack1.centerAlignContent();
stack1.spacing = 6;

const symbol1 = stack1.addImage(SFSymbol.named("battery.100").image);
symbol1.imageSize = new Size(26, 26);
symbol1.tintColor = new Color("#34C759");

const text2 = stack1.addText("" + String(data.d0) + "%");
text2.font = Font.boldSystemFont(30);

Script.setWidget(w);
if (!config.runsInWidget) await w.presentMedium();
Script.complete();
