// 진행률: 오늘·이번 주·이번 달·올해가 얼마나 지났는지 막대로 보여줍니다.
const BG = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
const FG = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
const SUB = Color.dynamic(new Color("#6E6E73"), new Color("#98989D"));
const COLORS = ["#FF9F0A", "#30D158", "#0A84FF", "#BF5AF2"];

const now = new Date();
const startOfWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() - ((now.getDay() + 6) % 7));
const rows = [
  ["오늘", new Date(now.getFullYear(), now.getMonth(), now.getDate()), new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)],
  ["이번 주", startOfWeek, new Date(startOfWeek.getTime() + 7 * 86400000)],
  ["이번 달", new Date(now.getFullYear(), now.getMonth(), 1), new Date(now.getFullYear(), now.getMonth() + 1, 1)],
  [`${now.getFullYear()}년`, new Date(now.getFullYear(), 0, 1), new Date(now.getFullYear() + 1, 0, 1)],
].map(([label, start, end]) => [label, Math.min(1, Math.max(0, (now - start) / (end - start)))]);

function bar(value, color, width) {
  const h = 8;
  const ctx = new DrawContext();
  ctx.size = new Size(width, h);
  ctx.opaque = false;
  ctx.respectScreenScale = true;
  const track = new Path();
  track.addRoundedRect(new Rect(0, 0, width, h), h / 2, h / 2);
  ctx.addPath(track);
  ctx.setFillColor(new Color("#8E8E93", 0.25));
  ctx.fillPath();
  const fill = new Path();
  fill.addRoundedRect(new Rect(0, 0, Math.max(h, width * value), h), h / 2, h / 2);
  ctx.addPath(fill);
  ctx.setFillColor(new Color(color));
  ctx.fillPath();
  return ctx.getImage();
}

const family = config.widgetFamily ?? "medium";
const shown = family === "small" ? rows.slice(0, 3) : rows;
const width = family === "small" ? 130 : 290;

const w = new ListWidget();
w.backgroundColor = BG;
w.setPadding(12, 14, 12, 14);
shown.forEach(([label, value], i) => {
  const head = w.addStack();
  const l = head.addText(label);
  l.font = Font.semiboldSystemFont(12);
  l.textColor = FG;
  head.addSpacer();
  const p = head.addText(`${(value * 100).toFixed(value < 0.1 ? 1 : 0)}%`);
  p.font = Font.mediumSystemFont(12);
  p.textColor = SUB;
  w.addSpacer(3);
  const img = w.addImage(bar(value, COLORS[i], width));
  img.imageSize = new Size(width, 8);
  w.addSpacer(family === "large" ? 14 : 6);
});
w.refreshAfterDate = new Date(Date.now() + 30 * 60 * 1000);

if (config.runsInWidget) Script.setWidget(w);
else await w.presentMedium();
Script.complete();
