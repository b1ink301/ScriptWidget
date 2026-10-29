// 6. 날씨 위젯: Location · Request · FileManager 캐시 · refreshAfterDate · LinearGradient · DateFormatter
// 위치를 못 얻으면 서울, 네트워크가 안 되면 마지막으로 받은 날씨를 보여줍니다.
const SEOUL = { latitude: 37.5665, longitude: 126.978 };

let loc = SEOUL;
try {
  Location.setAccuracyToKilometer();
  loc = await Location.current();
} catch (e) {
  console.warn("location unavailable:", e.message);
}

let place = loc === SEOUL ? "Seoul" : "";
try {
  const [p] = await Location.reverseGeocode(loc.latitude, loc.longitude);
  place = p?.locality ?? p?.subLocality ?? p?.administrativeArea ?? place;
} catch (e) {
  console.log("no geocoder:", e.message);
}

const fm = FileManager.local();
const dir = fm.joinPath(fm.documentsDirectory(), "weather");
const cachePath = fm.joinPath(dir, "last.json");
const url = "https://api.open-meteo.com/v1/forecast" +
  `?latitude=${loc.latitude.toFixed(4)}&longitude=${loc.longitude.toFixed(4)}` +
  "&current=temperature_2m,weather_code,is_day&timezone=auto";

let data;
try {
  data = await new Request(url).loadJSON();
  if (!fm.fileExists(dir)) fm.createDirectory(dir);
  fm.writeString(cachePath, JSON.stringify({ savedAt: Date.now(), data }));
} catch (e) {
  const cached = fm.readString(cachePath);
  if (!cached) throw e;
  console.warn("using cached weather:", e.message);
  data = JSON.parse(cached).data;
}

const current = data.current;
const CODES = [
  [[0], "☀️", "Clear"], [[1], "🌤", "Mainly clear"], [[2], "⛅️", "Partly cloudy"], [[3], "☁️", "Overcast"],
  [[45, 48], "🌫", "Fog"], [[51, 53, 55, 56, 57], "🌦", "Drizzle"],
  [[61, 63, 65, 66, 67, 80, 81, 82], "🌧", "Rain"], [[71, 73, 75, 77, 85, 86], "🌨", "Snow"],
  [[95, 96, 99], "⛈", "Thunderstorm"],
];
const [, icon, label] = CODES.find(([codes]) => codes.includes(current.weather_code)) ?? [null, "🌡", "Unknown"];
const night = current.is_day === 0;

const w = new ListWidget();
const g = new LinearGradient();
g.colors = night ? [new Color("#141E30"), new Color("#243B55")] : [new Color("#2980B9"), new Color("#6DD5FA")];
g.startPoint = new Point(0, 0);
g.endPoint = new Point(1, 1);
w.backgroundGradient = g;
w.setPadding(14, 14, 14, 14);

const head = w.addStack();
const where = head.addText(place || "📍");
where.font = Font.semiboldSystemFont(13);
where.textColor = Color.white();
where.lineLimit = 1;
head.addSpacer();
head.addText(icon).font = Font.systemFont(20);

w.addSpacer();
const temp = w.addText(`${Math.round(current.temperature_2m)}°`);
temp.font = Font.boldSystemFont(40);
temp.textColor = Color.white();

const desc = w.addText(label);
desc.font = Font.mediumSystemFont(13);
desc.textColor = new Color("#FFFFFF", 0.85);

const df = new DateFormatter();
df.useNoDateStyle();
df.useShortTimeStyle();
const updated = w.addText(`updated ${df.string(new Date())}`);
updated.font = Font.systemFont(10);
updated.textColor = new Color("#FFFFFF", 0.7);

// Weather doesn't change every 15 minutes: skip periodic refreshes for 30 minutes.
w.refreshAfterDate = new Date(Date.now() + 30 * 60 * 1000);
Script.setWidget(w);
