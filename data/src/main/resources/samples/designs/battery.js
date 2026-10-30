// scriptwidget-design: {"refreshMinutes":15,"children":[{"type":"text","text":"배터리","color":"#FF8E8E93","size":13.0},{"type":"stack","spacing":6.0,"children":[{"type":"symbol","name":"battery.100","color":"#FF34C759","size":26.0},{"type":"text","text":"{value}%","data":{"type":"battery"},"size":30.0,"weight":"bold"}]}]}
// scriptwidget-design-hash: 6dfbcee56e0e13f2
// 디자이너로 만든 위젯입니다. 디자이너에서 열어 고치세요.
// 여기서 코드를 직접 고치면 디자이너에서 다시 열 때 고친 내용이 사라집니다.

// 데이터 블록: 실패하면 "–"를 보여 줍니다.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }

await load("d0", async () => Math.round(Device.batteryLevel() * 100));

const w = new ListWidget();
w.refreshAfterDate = new Date(Date.now() + 15 * 60 * 1000);

const text1 = w.addText("배터리");
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
