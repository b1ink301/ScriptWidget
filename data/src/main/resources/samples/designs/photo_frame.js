// scriptwidget-design: {"background":{"color":"#FF1C1C1E","image":"photo"},"padding":12.0,"refreshMinutes":60,"children":[{"type":"spacer"},{"type":"text","text":"{value}","data":{"type":"dateText"},"color":"#FFFFFFFF","size":15.0,"weight":"semibold"}]}
// scriptwidget-design-hash: e3fdd1a0d08a3f08
// 디자이너로 만든 위젯입니다. 디자이너에서 열어 고치세요.
// 여기서 코드를 직접 고치면 디자이너에서 다시 열 때 고친 내용이 사라집니다.

// 데이터 블록: 실패하면 "–"를 보여 줍니다.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }

await load("d0", async () => formatDate(new Date(), "M월 d일 EEEE"));

// 사진은 파일 북마크에서 읽습니다(설정 → 파일 북마크). 없으면 빈 자리로 둡니다.
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
