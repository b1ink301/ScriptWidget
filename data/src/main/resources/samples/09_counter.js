// 9. 인터랙티브 위젯 (Android 확장): 버튼을 탭하면 스크립트가 args.widgetAction 과 함께 다시 실행됩니다.
const fm = FileManager.local();
const path = fm.joinPath(fm.documentsDirectory(), "counter.json");
const state = JSON.parse(fm.readString(path) ?? '{"count":0}');

switch (args.widgetAction) {
  case "increment": state.count += 1; break;
  case "decrement": state.count -= 1; break;
  case "reset": state.count = 0; break;
}
state.updated = Date.now();
fm.writeString(path, JSON.stringify(state));

const w = new ListWidget();
const g = new LinearGradient();
g.colors = [new Color("#5B247A"), new Color("#1BCEDF")];
g.startPoint = new Point(0, 0);
g.endPoint = new Point(1, 1);
w.backgroundGradient = g;
w.setPadding(12, 12, 12, 12);

const title = w.addText("Counter");
title.font = Font.semiboldSystemFont(13);
title.textColor = new Color("#FFFFFF", 0.8);

w.addSpacer();
const count = w.addText(String(state.count));
count.font = Font.boldSystemFont(44);
count.textColor = Color.white();
count.centerAlignText();
w.addSpacer();

const buttons = w.addStack();
buttons.centerAlignContent();
buttons.addButton("−", "decrement");
buttons.addSpacer();
buttons.addButton("↺", "reset");
buttons.addSpacer();
buttons.addButton("+", "increment");

Script.setWidget(w);
