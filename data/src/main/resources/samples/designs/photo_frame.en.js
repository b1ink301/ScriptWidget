// scriptwidget-design: {"background":{"color":"#FF1C1C1E","image":"photo"},"padding":12.0,"refreshMinutes":60,"children":[{"type":"spacer"},{"type":"text","text":"{value}","data":{"type":"dateText","format":"EEEE, MMM d"},"color":"#FFFFFFFF","size":15.0,"weight":"semibold"}]}
// scriptwidget-design-hash: 2cc60e9f8cc1ee84
// Made in the widget designer. Open it there to change it.
// Edits made to this code are lost when the designer saves again.

// Data blocks: show "–" when a value can't be loaded.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }

await load("d0", async () => formatDate(new Date(), "EEEE, MMM d"));

// Pictures come from file bookmarks (Settings → File bookmarks); a missing one leaves a placeholder.
function bookmarkImage(name) {
  const fm = FileManager.local();
  try { return fm.bookmarkExists(name) ? fm.readImage(fm.bookmarkedPath(name)) : null; } catch (e) { return null; }
}


const w = new ListWidget();
w.backgroundColor = new Color("#1C1C1E");
const bgImage = bookmarkImage("photo");
if (bgImage) w.backgroundImage = bgImage;
w.setPadding(12, 12, 12, 12);
w.refreshAfterDate = new Date(Date.now() + 60 * 60 * 1000);

w.addSpacer();

const text1 = w.addText("" + String(data.d0) + "");
text1.font = Font.semiboldSystemFont(15);
text1.textColor = new Color("#FFFFFF");

Script.setWidget(w);
if (!config.runsInWidget) await w.presentMedium();
Script.complete();
