[한국어](json-widget.ko.md)

# Widgets from a URL (JSON)

Build a home screen widget from values in the JSON a web API returns, without writing code. Any API that **returns numbers or text from a single address** works: weather, exchange rates, coin prices, GitHub stars and so on.

The result is an ordinary designer widget. You can change its sizes, colors and layout, or add more elements, in the designer later.

## Quick start

1. **My scripts** tab → **+ New script** (top right) → **🔗 From a URL (JSON)**
2. Tap one of the **examples** at the bottom (e.g. **Seoul weather**). The address field is filled in.
3. Tap **Load**. The values in the response are listed.
4. Tick the values to show (up to 6), e.g. `current.temperature_2m` and `current.relative_humidity_2m`.
5. Change the **Title** (left blank, it's the address's host name) and tap **Create**.
6. The designer opens, with the real values in the preview at the top. Adjust it as you like; it's saved when you go back.
7. Add a ScriptWidget widget to the home screen and pick this script.

## The screen

### Address and Load

- Enter an address starting with `https://`. If opening it in a browser shows text starting with `{ ... }` or `[ ... ]`, it's JSON.
- **Load** fetches it once, only to show which values there are. The widget requests the same address again on every refresh.

### The list of values

Each row shows a **path** above and its **current value** below.

| Response | Path in the list | Current value |
|---|---|---|
| `{"data": {"amount": "76712.6"}}` | `data.amount` | 76712.6 |
| `{"rates": {"KRW": 1342.9}}` | `rates.KRW` | 1342.9 |
| `{"items": [{"title": "first"}, {"title": "second"}]}` | `items.0.title`, `items.1.title` | first, second |

- A **path** is the names from the outside in, joined with dots (`.`).
- **Arrays** (`[ ... ]`) use positions, counting from 0; the list shows the first 3 items. `items.0.title` means "the title of the first item".
- Long values are shortened in the list; the widget gets the whole value.
- For very large responses only the first 200 values are listed.

### Layout

- **One value**: shown big under the title. Good for a single number such as stars or a price.
- **Several values**: stacked `name … value` rows. The name is the last part of the path, made readable (`stargazers_count` → "Stargazers count", `windSpeed` → "Wind speed"); you can rename them in the designer.
- Refreshes are requested every **30 minutes**. Change it in the designer: pick **🧩 Widget** in the outline, then **Requested refresh interval**.

## Examples

The same addresses as the example buttons in the app. None needs an account or an API key.

| Example | Address | Values to pick |
|---|---|---|
| Seoul weather ([Open-Meteo](https://open-meteo.com/)) | `https://api.open-meteo.com/v1/forecast?latitude=37.57&longitude=126.98&current=temperature_2m,relative_humidity_2m,wind_speed_10m` | `current.temperature_2m`, `current.relative_humidity_2m`, `current.wind_speed_10m` |
| Dollar exchange rates ([Frankfurter](https://frankfurter.dev/)) | `https://api.frankfurter.dev/v1/latest?base=USD&symbols=KRW,JPY,EUR` | `rates.KRW`, `rates.JPY`, `rates.EUR` |
| Bitcoin price (Coinbase) | `https://api.coinbase.com/v2/prices/BTC-USD/spot` | `data.amount` |
| A GitHub repository | `https://api.github.com/repos/b1ink301/ScriptWidget` | `stargazers_count`, `forks_count`, `open_issues_count` |

For another city's weather, change `latitude` and `longitude` in the address. For another repository, change what follows `repos/` to `owner/repository`.

## Styling

After **Create**, edit it in the designer like any widget.

- **Units**: select a value's text and change its **Text** property to `{value}°C`, `${value}`, `⭐ {value}` and so on. The value goes where `{value}` is.
- **Another path**: change **JSON URL** and **Value path** of the same text.
- **More values**: **+ Element** at the bottom → **Data blocks** → **JSON value (URL)**, with the same address and another path. However many values come from one address, it's requested **once** per refresh.
- **Open on tap**: put a web page (e.g. a weather site) in **🧩 Widget** → **URL to open on tap** to open it when the widget is tapped.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| "Couldn't load JSON: HTTP 401" or 403 | The address needs an API key or a login. If the key goes in the address (`?appid=…`, `?key=…`), include it. APIs that need the key in a header can't be used here; write the widget in code. |
| "HTTP 404" | The address is wrong. Open it in a browser first. |
| An error like "Unexpected JSON token" | The address is a web page (HTML), not JSON. Find the service's **API** address. |
| "There are no values in this response" | The response is an empty object (`{}`) or array (`[]`). Check the conditions in the address (dates, search terms, …). |
| The widget shows `–` | The value couldn't be fetched on refresh: no network, the server changed the response, or there's no value at that path any more. Check the preview in the designer and fix the path. |
| An array position points at a different item | Arrays are picked by position (`items.0` is "the first one now"). With APIs that put new items first (latest posts, …), the widget always shows whatever is first at the time. |
| Values update late | Refreshes follow the interval (30 minutes by default) and Android's battery rules. Tap the widget to refresh it now. See **Background refresh policy** in the README. |

## Good to know

- An API key in the address is stored as-is in the script's code. Remove it before exporting the script or sharing it in a gallery. (Export diagnostics hides everything after `?` in addresses.)
- Free APIs may limit the number of requests; don't set the refresh interval too short.
- For more involved logic (the difference of two values, converting dates, …), tap **Code** to see the generated Scriptable code and edit it in the editor. A script edited by hand opens in the editor from then on.
