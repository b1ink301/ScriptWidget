// 세계 시계: 여러 도시의 현재 시각을 보여줍니다. 시각은 위젯이 스스로 갱신합니다.
// 위젯 파라미터: "도시=시간대" 를 쉼표로. 예) "뉴욕=America/New_York,파리=Europe/Paris"
const BG = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
const FG = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
const SUB = Color.dynamic(new Color("#6E6E73"), new Color("#98989D"));
const DEFAULT = "서울=Asia/Seoul,뉴욕=America/New_York,런던=Europe/London,도쿄=Asia/Tokyo";
const cities = (args.widgetParameter || DEFAULT).split(",").map((s) => s.split("=").map((x) => x.trim())).filter((x) => x.length === 2);
const family = config.widgetFamily ?? "medium";
const shown = cities.slice(0, family === "small" ? 3 : family === "medium" ? 4 : 8);

const now = new Date();
const fmt = (tz, opts) => new Intl.DateTimeFormat("ko-KR", { timeZone: tz, ...opts }).format(now);
const local = Number(fmt(Intl.DateTimeFormat().resolvedOptions().timeZone, { day: "numeric" }).replace(/\D/g, ""));

const w = new ListWidget();
w.backgroundColor = BG;
w.setPadding(12, 14, 12, 14);
for (const [name, tz] of shown) {
  const row = w.addStack();
  row.centerAlignContent();
  const n = row.addText(name);
  n.font = Font.semiboldSystemFont(13);
  n.textColor = FG;
  row.addSpacer();
  let time = "—", day = "";
  try {
    time = fmt(tz, { hour: "2-digit", minute: "2-digit", hour12: false });
    const d = Number(fmt(tz, { day: "numeric" }).replace(/\D/g, ""));
    day = d === local ? "" : d > local || (local > 27 && d === 1) ? " +1" : " -1";
  } catch (e) {
    time = "시간대 오류";
  }
  const t = row.addText(time + day);
  t.font = Font.mediumMonospacedSystemFont(family === "large" ? 16 : 13);
  t.textColor = FG;
  w.addSpacer(family === "large" ? 8 : 4);
}
w.addSpacer();
w.refreshAfterDate = new Date(Math.ceil(Date.now() / 300000) * 300000);

if (config.runsInWidget) Script.setWidget(w);
else await w.presentMedium();
Script.complete();
