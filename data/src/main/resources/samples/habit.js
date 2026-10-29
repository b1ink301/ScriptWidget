// 물 마시기·습관 체크: + 를 누를 때마다 오늘 횟수가 늘어나고, 날짜가 바뀌면 0부터 시작합니다.
// 위젯 파라미터: "이름 목표" 예) "물 8", "스트레칭 3"
const [nameParam, goalParam] = (args.widgetParameter ?? "").trim().split(/\s+/);
const name = nameParam || "물";
const goal = Math.max(1, parseInt(goalParam) || 8);

const fm = FileManager.local();
const path = fm.joinPath(fm.documentsDirectory(), `habit-${name}.json`);
const today = new Date().toISOString().slice(0, 10);
let state = JSON.parse(fm.readString(path) ?? "{}");
if (state.day !== today) state = { day: today, count: 0, history: { ...(state.history ?? {}), ...(state.day ? { [state.day]: state.count } : {}) } };

if (args.widgetAction === "add") state.count += 1;
if (args.widgetAction === "undo") state.count = Math.max(0, state.count - 1);
fm.writeString(path, JSON.stringify(state));

const done = state.count >= goal;
const w = new ListWidget();
const g = new LinearGradient();
g.colors = done ? [new Color("#30D158"), new Color("#0A8F3C")] : [new Color("#64D2FF"), new Color("#0A84FF")];
g.locations = [0, 1];
w.backgroundGradient = g;
w.setPadding(12, 14, 12, 14);

const title = w.addText(`${name} ${done ? "완료 🎉" : ""}`);
title.font = Font.semiboldSystemFont(13);
title.textColor = new Color("#FFFFFF", 0.85);
w.addSpacer();
const big = w.addText(`${state.count}`);
big.font = Font.heavySystemFont(40);
big.textColor = Color.white();
const sub = w.addText(`목표 ${goal}회`);
sub.font = Font.systemFont(11);
sub.textColor = new Color("#FFFFFF", 0.8);
w.addSpacer();
const buttons = w.addStack();
buttons.addButton("−", "undo");
buttons.addSpacer();
buttons.addButton("+", "add");

if (config.runsInWidget) Script.setWidget(w);
else await w.presentSmall();
Script.complete();
