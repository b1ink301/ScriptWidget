// scriptwidget-design: {"url":"https://github.com/b1ink301/ScriptWidget","refreshMinutes":60,"children":[{"type":"text","text":"ScriptWidget","color":"#FF8E8E93","size":13.0},{"type":"text","text":"⭐ {value}","data":{"type":"json","url":"https://api.github.com/repos/b1ink301/ScriptWidget","path":"stargazers_count"},"size":30.0,"weight":"bold"}]}
// scriptwidget-design-hash: ddd1eaf8642eb637
// 디자이너로 만든 위젯입니다. 디자이너에서 열어 고치세요.
// 여기서 코드를 직접 고치면 디자이너에서 다시 열 때 고친 내용이 사라집니다.

// 데이터 블록: 실패하면 "–"를 보여 줍니다.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }
function pick(json, path) {
  const value = path.split(".").filter(Boolean).reduce((o, k) => (o == null ? undefined : o[k]), json);
  return value !== null && typeof value === "object" ? JSON.stringify(value) : value;
}

await load("d0", async () => pick(await new Request("https://api.github.com/repos/b1ink301/ScriptWidget").loadJSON(), "stargazers_count"));

const w = new ListWidget();
w.url = "https://github.com/b1ink301/ScriptWidget";
w.refreshAfterDate = new Date(Date.now() + 60 * 60 * 1000);

const text1 = w.addText("ScriptWidget");
text1.font = Font.systemFont(13);
text1.textColor = new Color("#8E8E93");

const text2 = w.addText("⭐ " + String(data.d0) + "");
text2.font = Font.boldSystemFont(30);

Script.setWidget(w);
if (!config.runsInWidget) await w.presentMedium();
Script.complete();
