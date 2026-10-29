// 사진 액자: 파일 북마크 "photo" 의 사진을 위젯 가득 보여줍니다.
// 스크립트 목록 ⋮ → 파일 북마크 → 이름 "photo" 로 사진을 추가하세요. (파라미터로 다른 이름도 가능)
const fm = FileManager.local();
const name = (args.widgetParameter ?? "").trim() || "photo";
const w = new ListWidget();
w.setPadding(10, 12, 10, 12);

if (fm.bookmarkExists(name)) {
  w.backgroundImage = fm.readImage(fm.bookmarkedPath(name));
  w.addSpacer();
  const d = new Date();
  const caption = w.addText(`${d.getMonth() + 1}월 ${d.getDate()}일`);
  caption.font = Font.semiboldSystemFont(12);
  caption.textColor = Color.white();
  caption.shadowColor = new Color("#000000", 0.6);
  caption.shadowRadius = 3;
} else {
  w.backgroundColor = Color.dynamic(new Color("#F2F2F7"), new Color("#2C2C2E"));
  const t = w.addText("🖼️ 사진 액자");
  t.font = Font.boldSystemFont(15);
  t.textColor = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
  w.addSpacer(6);
  const hint = w.addText(`목록 ⋮ → 파일 북마크에서 "${name}" 이름으로 사진을 추가하세요.`);
  hint.font = Font.systemFont(12);
  hint.textColor = Color.dynamic(new Color("#6E6E73"), new Color("#98989D"));
}

if (config.runsInWidget) Script.setWidget(w);
else await w.presentMedium();
Script.complete();
