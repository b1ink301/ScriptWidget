// 7. 쇼케이스: SFSymbol · DrawContext · Device · 이미지 정렬/틴트 · 스택 테두리 · RelativeDateTimeFormatter
const battery = Device.batteryLevel();

// Battery ring drawn with DrawContext
const size = 120;
const ctx = new DrawContext();
ctx.size = new Size(size, size);
ctx.opaque = false;
ctx.respectScreenScale = true;
ctx.setStrokeColor(new Color("#FFFFFF", 0.25));
ctx.setLineWidth(12);
ctx.strokeEllipse(new Rect(8, 8, size - 16, size - 16));
const ring = new Path();
const steps = Math.max(1, Math.round(60 * battery));
for (let i = 0; i <= steps; i++) {
  const a = -Math.PI / 2 + (2 * Math.PI * battery * i) / steps;
  const p = new Point(size / 2 + (size / 2 - 8) * Math.cos(a), size / 2 + (size / 2 - 8) * Math.sin(a));
  i === 0 ? ring.move(p) : ring.addLine(p);
}
ctx.addPath(ring);
ctx.setStrokeColor(battery > 0.2 ? new Color("#34C759") : new Color("#FF3B30"));
ctx.strokePath();
ctx.setFont(Font.boldSystemFont(26));
ctx.setTextColor(Color.white());
ctx.setTextAlignedCenter();
ctx.drawTextInRect(`${Math.round(battery * 100)}%`, new Rect(0, size / 2 - 17, size, 40));

const w = new ListWidget();
const g = new LinearGradient();
g.colors = [new Color("#232526"), new Color("#414345")];
w.backgroundGradient = g;
w.setPadding(12, 12, 12, 12);

const row = w.addStack();
row.centerAlignContent();
row.spacing = 12;

const ringImage = row.addImage(ctx.getImage());
ringImage.imageSize = new Size(64, 64);

const info = row.addStack();
info.layoutVertically();
info.spacing = 4;
info.setPadding(6, 8, 6, 8);
info.cornerRadius = 10;
info.borderColor = new Color("#FFFFFF", 0.3);
info.borderWidth = 1;

const line = (symbol, text, color) => {
  const s = info.addStack();
  s.centerAlignContent();
  s.spacing = 6;
  const sym = SFSymbol.named(symbol);
  sym.applyFont(Font.systemFont(14));
  const icon = s.addImage(sym.image);
  icon.imageSize = new Size(14, 14);
  icon.tintColor = color;
  const t = s.addText(text);
  t.font = Font.mediumSystemFont(12);
  t.textColor = Color.white();
  t.lineLimit = 1;
};
line(Device.isCharging() ? "bolt.fill" : "battery.75", Device.isCharging() ? "Charging" : "On battery", new Color("#FFD60A"));
line("iphone", Device.model(), new Color("#64D2FF"));
line(Device.isUsingDarkAppearance() ? "moon.fill" : "sun.max.fill", Device.isUsingDarkAppearance() ? "Dark mode" : "Light mode", new Color("#BF5AF2"));

w.addSpacer();
const footer = w.addStack();
const rel = new RelativeDateTimeFormatter();
rel.useNamedDateTimeStyle();
const updated = footer.addText(`updated ${rel.string(new Date(), new Date())} · ${UUID.string().slice(0, 4)}`);
updated.font = Font.systemFont(10);
updated.textColor = new Color("#FFFFFF", 0.6);
footer.addSpacer();
const gear = footer.addImage(SFSymbol.named("gearshape").image);
gear.imageSize = new Size(12, 12);
gear.tintColor = new Color("#FFFFFF", 0.6);
gear.rightAlignImage();

w.url = URLScheme.forRunningScript();
Script.setWidget(w);
