// 코인 시세: 업비트 공개 API로 원화 시세와 24시간 변동률을 보여줍니다. (API 키 불필요)
// 위젯 파라미터에 코인을 쉼표로 적으세요. 예) "BTC,ETH,SOL"
const BG = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
const FG = Color.dynamic(new Color("#111111"), new Color("#F2F2F7"));
const SUB = Color.dynamic(new Color("#6E6E73"), new Color("#98989D"));
const UP = new Color("#FF3B30");   // 한국식: 상승 빨강
const DOWN = new Color("#0A84FF"); // 하락 파랑

const coins = (args.widgetParameter || "BTC,ETH,XRP").split(",").map((s) => s.trim().toUpperCase()).filter(Boolean);
const family = config.widgetFamily ?? "medium";
const shown = coins.slice(0, family === "small" ? 3 : family === "medium" ? 4 : 8);

const fm = FileManager.local();
const cache = fm.joinPath(fm.cacheDirectory(), "crypto.json");
let tickers;
try {
  const req = new Request(`https://api.upbit.com/v1/ticker?markets=${shown.map((c) => "KRW-" + c).join(",")}`);
  req.timeoutInterval = 10;
  tickers = await req.loadJSON();
  if (!Array.isArray(tickers)) throw new Error("bad response");
  fm.writeString(cache, JSON.stringify(tickers));
} catch (e) {
  tickers = JSON.parse(fm.readString(cache) ?? "[]");
}

const w = new ListWidget();
w.backgroundColor = BG;
w.setPadding(12, 14, 12, 14);
const head = w.addText("코인 시세");
head.font = Font.boldSystemFont(14);
head.textColor = FG;
w.addSpacer(6);

if (tickers.length === 0) {
  const t = w.addText("시세를 불러오지 못했어요");
  t.font = Font.systemFont(12);
  t.textColor = SUB;
}
for (const t of tickers) {
  const row = w.addStack();
  row.centerAlignContent();
  const name = row.addText(t.market.replace("KRW-", ""));
  name.font = Font.semiboldSystemFont(13);
  name.textColor = FG;
  row.addSpacer();
  const price = row.addText(Number(t.trade_price).toLocaleString("ko-KR"));
  price.font = Font.mediumMonospacedSystemFont(12);
  price.textColor = FG;
  if (family !== "small") {
    row.addSpacer(8);
    const rate = t.signed_change_rate * 100;
    const ch = row.addText(`${rate > 0 ? "▲" : rate < 0 ? "▼" : ""}${Math.abs(rate).toFixed(2)}%`);
    ch.font = Font.mediumSystemFont(11);
    ch.textColor = rate > 0 ? UP : rate < 0 ? DOWN : SUB;
  }
  w.addSpacer(4);
}
w.addSpacer();
const time = w.addText(new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" }) + " 기준");
time.font = Font.systemFont(9);
time.textColor = SUB;
w.refreshAfterDate = new Date(Date.now() + 10 * 60 * 1000);

if (config.runsInWidget) Script.setWidget(w);
else await w.presentMedium();
Script.complete();
