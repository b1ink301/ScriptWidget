// scriptwidget-design: {"background":{"gradient":["#FF2F80ED","#FF56CCF2"]},"refreshMinutes":60,"children":[{"type":"text","text":"{value}","data":{"type":"dateText"},"size":13.0},{"type":"text","text":"{value}°","data":{"type":"weather"},"size":40.0,"weight":"bold"},{"type":"text","text":"{value}","data":{"type":"weather","field":"Condition"},"size":15.0}]}
// scriptwidget-design-hash: da6342ac2cea2d2d
// 디자이너로 만든 위젯입니다. 디자이너에서 열어 고치세요.
// 여기서 코드를 직접 고치면 디자이너에서 다시 열 때 고친 내용이 사라집니다.

// 데이터 블록: 실패하면 "–"를 보여 줍니다.
const data = {};
async function load(key, read) {
  try { data[key] = await read(); } catch (e) { console.warn(`${key}: ${e}`); data[key] = "–"; }
  if (data[key] === undefined || data[key] === null) data[key] = "–";
}
function formatDate(date, pattern) { const f = new DateFormatter(); f.dateFormat = pattern; return f.string(date); }
let _weather;
async function weather() {
  if (_weather) return _weather;
  let loc = { latitude: 37.5665, longitude: 126.978 };
  try { Location.setAccuracyToKilometer(); loc = await Location.current(); } catch (e) {}
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}&current=temperature_2m,weather_code&timezone=auto`;
  return (_weather = (await new Request(url).loadJSON()).current);
}
function weatherLabel(code) {
  const labels = [[[0], "맑음"], [[1, 2], "구름 조금"], [[3], "흐림"], [[45, 48], "안개"],
    [[51, 53, 55, 56, 57], "이슬비"], [[61, 63, 65, 66, 67, 80, 81, 82], "비"],
    [[71, 73, 75, 77, 85, 86], "눈"], [[95, 96, 99], "뇌우"]];
  return (labels.find(([codes]) => codes.includes(code)) ?? [null, "–"])[1];
}

await load("d0", async () => formatDate(new Date(), "M월 d일 EEEE"));
await load("d1", async () => Math.round((await weather()).temperature_2m));
await load("d2", async () => weatherLabel((await weather()).weather_code));

const w = new ListWidget();
const bg = new LinearGradient();
bg.colors = [new Color("#2F80ED"), new Color("#56CCF2")];
bg.locations = [0, 1];
w.backgroundGradient = bg;
w.refreshAfterDate = new Date(Date.now() + 60 * 60 * 1000);

const text1 = w.addText("" + String(data.d0) + "");
text1.font = Font.systemFont(13);

const text2 = w.addText("" + String(data.d1) + "°");
text2.font = Font.boldSystemFont(40);

const text3 = w.addText("" + String(data.d2) + "");
text3.font = Font.systemFont(15);

Script.setWidget(w);
if (!config.runsInWidget) await w.presentMedium();
Script.complete();
