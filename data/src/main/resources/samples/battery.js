// 배터리: 잔량을 링으로 그리고 충전 상태를 보여줍니다.
const BG = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
const FG = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
const SUB = Color.dynamic(new Color("#6E6E73"), new Color("#98989D"));
const level = Device.batteryLevel();
const charging = Device.isCharging();
const color = charging ? new Color("#30D158") : level < 0.2 ? new Color("#FF453A") : level < 0.5 ? new Color("#FFD60A") : new Color("#0A84FF");

function ring(value) {
  const size = 120, width = 12;
  const ctx = new DrawContext();
  ctx.size = new Size(size, size);
  ctx.opaque = false;
  ctx.respectScreenScale = true;
  ctx.setStrokeColor(new Color("#8E8E93", 0.25));
  ctx.setLineWidth(width);
  ctx.strokeEllipse(new Rect(width / 2, width / 2, size - width, size - width));
  const path = new Path();
  const r = (size - width) / 2, c = size / 2;
  const steps = Math.max(1, Math.round(60 * value));
  for (let i = 0; i <= steps; i++) {
    const a = -Math.PI / 2 + (2 * Math.PI * value * i) / steps;
    const p = new Point(c + r * Math.cos(a), c + r * Math.sin(a));
    i === 0 ? path.move(p) : path.addLine(p);
  }
  ctx.addPath(path);
  ctx.setStrokeColor(color);
  ctx.strokePath();
  return ctx.getImage();
}

const w = new ListWidget();
w.backgroundColor = BG;
w.setPadding(12, 12, 12, 12);
const row = w.addStack();
row.centerAlignContent();
const img = row.addImage(ring(level));
img.imageSize = new Size(70, 70);
if (config.widgetFamily !== "small") {
  row.addSpacer(14);
  const col = row.addStack();
  col.layoutVertically();
  const name = col.addText(Device.model());
  name.font = Font.semiboldSystemFont(13);
  name.textColor = SUB;
  name.lineLimit = 1;
  const pct = col.addText(`${Math.round(level * 100)}%`);
  pct.font = Font.boldSystemFont(30);
  pct.textColor = FG;
  const state = col.addText(charging ? (Device.isFullyCharged() ? "완충됨" : "충전 중 ⚡") : "배터리 사용 중");
  state.font = Font.systemFont(12);
  state.textColor = color;
} else {
  w.addSpacer(6);
  const pct = w.addText(`${Math.round(level * 100)}%${charging ? " ⚡" : ""}`);
  pct.font = Font.boldSystemFont(20);
  pct.textColor = FG;
  pct.centerAlignText();
}
w.refreshAfterDate = new Date(Date.now() + 15 * 60 * 1000);

if (config.runsInWidget) Script.setWidget(w);
else await w.presentSmall();
Script.complete();
