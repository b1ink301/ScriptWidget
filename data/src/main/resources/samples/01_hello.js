// 1. Hello 위젯: 동기 스크립트, 텍스트 + 배경색
const w = new ListWidget();
w.backgroundColor = new Color("#1C1C1E");
w.setPadding(16, 16, 16, 16);

const title = w.addText("Hello, Widget 👋");
title.font = Font.boldSystemFont(20);
title.textColor = Color.white();

w.addSpacer(6);

const sub = w.addText(`family: ${config.widgetFamily} · param: ${args.widgetParameter ?? "-"}`);
sub.font = Font.systemFont(12);
sub.textColor = Color.gray();

w.addSpacer();

const time = w.addDate(new Date());
time.applyTimeStyle();
time.font = Font.mediumSystemFont(13);
time.textColor = new Color("#0A84FF");

Script.setWidget(w);
Script.complete();
