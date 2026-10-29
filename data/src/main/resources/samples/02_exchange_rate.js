// 2. API 위젯: await + Request.loadJSON()
// 위젯 파라미터로 통화 코드를 지정할 수 있습니다. (예: "JPY")
const base = (args.widgetParameter || "USD").toUpperCase();
const req = new Request(`https://open.er-api.com/v6/latest/${base}`);
req.timeoutInterval = 15;
const data = await req.loadJSON();
console.log("status", req.response.statusCode, "result", data.result);

if (data.result !== "success") throw new Error(`API error: ${data["error-type"]}`);

const w = new ListWidget();
w.backgroundColor = new Color("#0B3D2E");
w.setPadding(14, 14, 14, 14);

const head = w.addText(`💱 ${base} → KRW`);
head.font = Font.semiboldSystemFont(14);
head.textColor = new Color("#A7F3D0");

w.addSpacer();

// QuickJS has no Intl, so toLocaleString() options are ignored: format manually.
const fmt = (n, digits) => n.toFixed(digits).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
const rate = w.addText(fmt(data.rates.KRW, 1));
rate.font = Font.boldSystemFont(28);
rate.textColor = Color.white();
rate.lineLimit = 1;

if (config.widgetFamily !== "small") {
  const row = w.addStack();
  row.spacing = 12;
  for (const c of ["EUR", "JPY", "CNY"].filter((c) => c !== base)) {
    const t = row.addText(`${c} ${data.rates[c].toFixed(3)}`);
    t.font = Font.systemFont(12);
    t.textColor = new Color("#D1FAE5");
  }
}

w.addSpacer(4);
const d = new Date(data.time_last_update_unix * 1000);
const pad2 = (n) => String(n).padStart(2, "0");
const updated = w.addText(`updated ${d.getMonth() + 1}/${d.getDate()} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`);
updated.font = Font.systemFont(10);
updated.textColor = new Color("#6EE7B7");

Script.setWidget(w);
