// 8. 설정 UI: 앱에서 실행하면 Alert·UITable로 설정하고, 위젯에서는 저장된 설정으로 그립니다.
// (Scriptable에서 흔한 패턴: config.runsInApp 이면 설정 화면, 아니면 위젯)
const THEMES = {
  Ocean: ["#2193B0", "#6DD5ED"],
  Sunset: ["#EE0979", "#FF6A00"],
  Forest: ["#134E5E", "#71B280"],
  Night: ["#232526", "#414345"],
};

const fm = FileManager.local();
const path = fm.joinPath(fm.documentsDirectory(), "settings_ui.json");
const settings = Object.assign({ city: "Seoul", theme: "Ocean" }, JSON.parse(fm.readString(path) ?? "{}"));

if (config.runsInApp) {
  const alert = new Alert();
  alert.title = "도시 설정";
  alert.message = "위젯에 표시할 도시 이름";
  alert.addTextField("도시", settings.city);
  alert.addAction("저장");
  alert.addCancelAction("취소");
  if ((await alert.presentAlert()) === 0) settings.city = alert.textFieldValue(0).trim() || settings.city;

  const table = new UITable();
  table.showSeparators = true;
  const header = new UITableRow();
  header.isHeader = true;
  header.addText("테마 선택", "탭하면 적용됩니다");
  table.addRow(header);
  for (const [name, [from, to]] of Object.entries(THEMES)) {
    const row = new UITableRow();
    const title = row.addText(name, `${from} → ${to}`);
    title.titleColor = new Color(from);
    if (name === settings.theme) row.addText("✓").rightAligned();
    row.onSelect = () => { settings.theme = name; };
    table.addRow(row);
  }
  await table.present();

  fm.writeString(path, JSON.stringify(settings));
}

const [from, to] = THEMES[settings.theme] ?? THEMES.Ocean;
const w = new ListWidget();
const g = new LinearGradient();
g.colors = [new Color(from), new Color(to)];
g.startPoint = new Point(0, 0);
g.endPoint = new Point(1, 1);
w.backgroundGradient = g;
w.setPadding(14, 14, 14, 14);

const city = w.addText(`📍 ${settings.city}`);
city.font = Font.boldSystemFont(22);
city.textColor = Color.white();
w.addSpacer();
const theme = w.addText(`theme: ${settings.theme}`);
theme.font = Font.mediumSystemFont(12);
theme.textColor = new Color("#FFFFFF", 0.8);
const hint = w.addText("앱에서 실행하면 설정을 바꿀 수 있어요");
hint.font = Font.systemFont(10);
hint.textColor = new Color("#FFFFFF", 0.7);

w.url = URLScheme.forRunningScript();
Script.setWidget(w);
