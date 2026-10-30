// scriptwidget-design: {"children":[{"type":"text","text":"{value}","data":{"type":"dateText","format":"EEEE"},"color":"#FF8E8E93","size":13.0},{"type":"date","size":34.0,"weight":"bold"},{"type":"text","text":"{value}","data":{"type":"dateText","format":"M월 d일"},"size":15.0}]}
// scriptwidget-design-hash: b4596de3b578964e
// 디자이너로 만든 위젯입니다. 디자이너에서 열어 고치세요.
// 여기서 코드를 직접 고치면 디자이너에서 다시 열 때 고친 내용이 사라집니다.

// 데이터 블록: 실패하면 "–"를 보여 줍니다.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }

await load("d0", async () => formatDate(new Date(), "EEEE"));
await load("d1", async () => formatDate(new Date(), "M월 d일"));

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
