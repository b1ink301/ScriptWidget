// scriptwidget-design: {"background":{"gradient":["#FF2F80ED","#FF56CCF2"]},"refreshMinutes":60,"children":[{"type":"text","text":"{value}","data":{"type":"dateText","format":"EEEE, MMM d"},"size":13.0},{"type":"text","text":"{value}°","data":{"type":"weather"},"size":40.0,"weight":"bold"},{"type":"text","text":"{value}","data":{"type":"weather","field":"Condition"},"size":15.0}]}
// scriptwidget-design-hash: 4d9902b8c44ba40d
// Made in the widget designer. Open it there to change it.
// Edits made to this code are lost when the designer saves again.

// Data blocks: show "–" when a value can't be loaded.
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
  const labels = [[[0], "Clear"], [[1, 2], "Partly cloudy"], [[3], "Cloudy"], [[45, 48], "Fog"],
    [[51, 53, 55, 56, 57], "Drizzle"], [[61, 63, 65, 66, 67, 80, 81, 82], "Rain"],
    [[71, 73, 75, 77, 85, 86], "Snow"], [[95, 96, 99], "Thunderstorm"]];
  return (labels.find(([codes]) => codes.includes(code)) ?? [null, "–"])[1];
}

await load("d0", async () => formatDate(new Date(), "EEEE, MMM d"));
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
