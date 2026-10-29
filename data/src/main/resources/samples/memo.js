// 메모: 앱에서 실행하면 메모를 고칠 수 있고, 위젯은 저장된 메모를 보여줍니다.
const fm = FileManager.local();
const dir = fm.joinPath(fm.documentsDirectory(), "memo");
const path = fm.joinPath(dir, "note.txt");
if (!fm.fileExists(dir)) fm.createDirectory(dir, true);

if (config.runsInApp) {
  const a = new Alert();
  a.title = "메모";
  a.message = "줄을 바꾸려면 \\n 을 입력하세요";
  a.addTextField("메모", (fm.readString(path) ?? "").replace(/\n/g, "\\n"));
  a.addAction("저장");
  a.addCancelAction("취소");
  if ((await a.present()) === 0) fm.writeString(path, a.textFieldValue().replace(/\\n/g, "\n"));
}

const note = fm.readString(path) ?? "앱에서 이 스크립트를 실행해 메모를 적어 보세요.";
const w = new ListWidget();
w.backgroundColor = Color.dynamic(new Color("#FFF8C4"), new Color("#3A3520"));
w.setPadding(12, 14, 12, 14);
const head = w.addText("📝 메모");
head.font = Font.semiboldSystemFont(12);
head.textColor = Color.dynamic(new Color("#8A6D00"), new Color("#E5C547"));
w.addSpacer(6);
const body = w.addText(note);
body.font = Font.mediumSystemFont(config.widgetFamily === "large" ? 17 : 14);
body.textColor = Color.dynamic(new Color("#2B2B2B"), new Color("#F5F0D8"));
body.lineLimit = config.widgetFamily === "large" ? 14 : 5;
w.addSpacer();
const updated = fm.modificationDate(path);
if (updated) {
  const t = w.addText(`${updated.getMonth() + 1}/${updated.getDate()} 수정`);
  t.font = Font.systemFont(9);
  t.textColor = Color.dynamic(new Color("#8A6D00"), new Color("#B9A35A"));
}

if (config.runsInWidget) Script.setWidget(w);
else await w.presentMedium();
Script.complete();
