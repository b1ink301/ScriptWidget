// 다가오는 생일: 연락처의 생일을 가까운 순서로 보여줍니다. (연락처 권한 필요)
const BG = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
const FG = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
const SUB = Color.dynamic(new Color("#6E6E73"), new Color("#98989D"));
const PINK = new Color("#FF375F");
const max = { small: 3, medium: 4, large: 10 }[config.widgetFamily ?? "medium"] ?? 4;

const today = new Date();
today.setHours(0, 0, 0, 0);
let people = [];
let problem = null;
try {
  const contacts = await Contact.all(await ContactsContainer.all());
  people = contacts
    .filter((c) => c.birthday)
    .map((c) => {
      const b = c.birthday;
      let next = new Date(today.getFullYear(), b.getMonth(), b.getDate());
      if (next < today) next = new Date(today.getFullYear() + 1, b.getMonth(), b.getDate());
      const name = `${c.familyName ?? ""}${c.givenName ?? ""}`.trim() || c.nickname || "이름 없음";
      return { name, next, days: Math.round((next - today) / 86400000) };
    })
    .sort((a, b) => a.days - b.days);
} catch (e) {
  problem = "연락처 권한을 허용해 주세요";
}

const w = new ListWidget();
w.backgroundColor = BG;
w.setPadding(12, 14, 12, 14);
const title = w.addText("🎂 다가오는 생일");
title.font = Font.boldSystemFont(14);
title.textColor = PINK;
w.addSpacer(8);

if (problem || people.length === 0) {
  const t = w.addText(problem ?? "생일이 등록된 연락처가 없어요");
  t.font = Font.systemFont(12);
  t.textColor = SUB;
}
for (const p of people.slice(0, max)) {
  const row = w.addStack();
  const n = row.addText(p.name);
  n.font = Font.semiboldSystemFont(13);
  n.textColor = FG;
  n.lineLimit = 1;
  row.addSpacer();
  const d = row.addText(p.days === 0 ? "오늘 🎉" : `${p.days}일 후`);
  d.font = Font.mediumSystemFont(12);
  d.textColor = p.days <= 7 ? PINK : SUB;
  w.addSpacer(5);
}
w.addSpacer();
w.refreshAfterDate = new Date(today.getTime() + 86400000);

if (config.runsInWidget) Script.setWidget(w);
else await w.presentMedium();
Script.complete();
