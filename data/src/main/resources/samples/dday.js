// D-day: 위젯 파라미터에 "날짜 이름"을 적으세요. 예) "2026-12-25 크리스마스", "2027-03-01 여행"
// 날짜가 지났으면 D+N 으로 보여줍니다.
const FG = Color.white();
const SUB = new Color("#FFFFFF", 0.75);
const param = (args.widgetParameter ?? "").trim();
const match = param.match(/^(\d{4})[-.\/](\d{1,2})[-.\/](\d{1,2})\s*(.*)$/);

const now = new Date();
const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
const target = match ? new Date(+match[1], +match[2] - 1, +match[3]) : new Date(now.getFullYear(), 11, 25);
const name = match ? (match[4] || "D-day") : "크리스마스";
const diff = Math.round((target - today) / 86400000);
const label = diff === 0 ? "D-DAY" : diff > 0 ? `D-${diff}` : `D+${-diff}`;

const w = new ListWidget();
const g = new LinearGradient();
g.colors = [new Color("#FF6B6B"), new Color("#845EF7")];
g.locations = [0, 1];
g.startPoint = new Point(0, 0);
g.endPoint = new Point(1, 1);
w.backgroundGradient = g;
w.setPadding(14, 14, 14, 14);

const n = w.addText(name);
n.font = Font.semiboldSystemFont(14);
n.textColor = SUB;
n.lineLimit = 1;
w.addSpacer();
const big = w.addText(label);
big.font = Font.heavySystemFont(config.widgetFamily === "small" ? 34 : 44);
big.textColor = FG;
big.minimumScaleFactor = 0.5;
big.lineLimit = 1;
w.addSpacer(4);
const d = w.addText(target.toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric", weekday: "short" }));
d.font = Font.systemFont(12);
d.textColor = SUB;
if (!match) {
  const hint = w.addText("파라미터에 \"YYYY-MM-DD 이름\"을 적으세요");
  hint.font = Font.systemFont(9);
  hint.textColor = SUB;
}
w.refreshAfterDate = new Date(today.getTime() + 86400000);

if (config.runsInWidget) Script.setWidget(w);
else await w.presentSmall();
Script.complete();
