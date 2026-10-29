// RSS 뉴스: RSS 주소의 최신 기사 제목을 보여주고, 탭하면 기사를 엽니다.
// 위젯 파라미터에 RSS 주소를 적으세요. 기본: 연합뉴스 최신 기사
const FEED = (args.widgetParameter || "https://www.yna.co.kr/rss/news.xml").trim();
const BG = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
const FG = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
const SUB = Color.dynamic(new Color("#6E6E73"), new Color("#98989D"));
const family = config.widgetFamily ?? "medium";
const max = { small: 3, medium: 4, large: 9 }[family] ?? 4;

const fm = FileManager.local();
const cache = fm.joinPath(fm.cacheDirectory(), "rss.json");

function parseRss(xml) {
  const items = [];
  let channel = "", current = null, text = "";
  const p = new XMLParser(xml);
  p.didStartElement = (name) => { text = ""; if (name === "item" || name === "entry") current = { title: "", link: "" }; };
  p.foundCharacters = (s) => { text += s; };
  p.didEndElement = (name) => {
    const value = text.trim();
    if (current) {
      if (name === "title") current.title = value;
      else if (name === "link" && value) current.link = value;
      else if (name === "item" || name === "entry") { items.push(current); current = null; }
    } else if (name === "title" && !channel) channel = value;
    text = "";
  };
  p.parse();
  return { channel, items };
}

let feed;
try {
  feed = parseRss(await new Request(FEED).loadString());
  if (feed.items.length === 0) throw new Error("empty feed");
  fm.writeString(cache, JSON.stringify(feed));
} catch (e) {
  feed = JSON.parse(fm.readString(cache) ?? '{"channel":"","items":[]}');
}

const w = new ListWidget();
w.backgroundColor = BG;
w.setPadding(12, 14, 12, 14);
const head = w.addText(feed.channel || "뉴스");
head.font = Font.boldSystemFont(14);
head.textColor = new Color("#FF9F0A");
head.lineLimit = 1;
w.addSpacer(6);
if (feed.items.length === 0) {
  const t = w.addText("기사를 불러오지 못했어요");
  t.font = Font.systemFont(12);
  t.textColor = SUB;
}
for (const item of feed.items.slice(0, max)) {
  const t = w.addText(item.title);
  t.font = Font.mediumSystemFont(family === "small" ? 12 : 13);
  t.textColor = FG;
  t.lineLimit = family === "large" ? 2 : 1;
  if (item.link) t.url = item.link;
  w.addSpacer(5);
}
w.addSpacer();
w.refreshAfterDate = new Date(Date.now() + 30 * 60 * 1000);

if (config.runsInWidget) Script.setWidget(w);
else await w.presentLarge();
Script.complete();
