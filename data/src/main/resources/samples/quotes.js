// 오늘의 명언: 날마다 다른 문장을 보여줍니다. (인터넷 불필요)
const QUOTES = [
  ["천 리 길도 한 걸음부터.", "속담"],
  ["오늘 할 수 있는 일을 내일로 미루지 마라.", "벤저민 프랭클린"],
  ["행동은 모든 성공의 기본 열쇠다.", "파블로 피카소"],
  ["배움에는 끝이 없다.", "속담"],
  ["작은 기회로부터 종종 위대한 업적이 시작된다.", "데모스테네스"],
  ["꿈을 계속 간직하고 있으면 반드시 실현할 때가 온다.", "괴테"],
  ["실패는 성공의 어머니다.", "속담"],
  ["가장 큰 영광은 한 번도 실패하지 않는 것이 아니라, 실패할 때마다 다시 일어서는 데 있다.", "공자"],
  ["단순함은 궁극의 정교함이다.", "레오나르도 다 빈치"],
  ["지금 이 순간을 즐겨라.", "호라티우스"],
  ["낙관주의는 성공으로 인도하는 믿음이다.", "헬렌 켈러"],
  ["시작이 반이다.", "아리스토텔레스"],
  ["웃음은 마음의 조깅이다.", "노먼 커즌스"],
  ["우리가 반복적으로 하는 것이 곧 우리 자신이다.", "아리스토텔레스"],
];
const now = new Date();
const dayOfYear = Math.floor((now - new Date(now.getFullYear(), 0, 0)) / 86400000);
const [text, who] = QUOTES[dayOfYear % QUOTES.length];

const w = new ListWidget();
const g = new LinearGradient();
g.colors = [new Color("#232526"), new Color("#414345")];
g.locations = [0, 1];
w.backgroundGradient = g;
w.setPadding(14, 16, 14, 16);
const q = w.addText("“");
q.font = Font.boldSystemFont(28);
q.textColor = new Color("#FFD60A");
const t = w.addText(text);
t.font = Font.mediumSystemFont(config.widgetFamily === "small" ? 13 : 16);
t.textColor = Color.white();
t.minimumScaleFactor = 0.6;
w.addSpacer();
const a = w.addText(`— ${who}`);
a.font = Font.systemFont(11);
a.textColor = new Color("#FFFFFF", 0.7);
a.rightAlignText();
w.refreshAfterDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);

if (config.runsInWidget) Script.setWidget(w);
else await w.presentMedium();
Script.complete();
