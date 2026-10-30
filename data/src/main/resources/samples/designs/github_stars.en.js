// scriptwidget-design: {"url":"https://github.com/b1ink301/ScriptWidget","refreshMinutes":60,"children":[{"type":"text","text":"ScriptWidget","color":"#FF8E8E93","size":13.0},{"type":"text","text":"⭐ {value}","data":{"type":"json","url":"https://api.github.com/repos/b1ink301/ScriptWidget","path":"stargazers_count"},"size":30.0,"weight":"bold"}]}
// scriptwidget-design-hash: 35581f3ac874e507
// Made in the widget designer. Open it there to change it.
// Edits made to this code are lost when the designer saves again.

// Data blocks: show "–" when a value can't be loaded.
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
