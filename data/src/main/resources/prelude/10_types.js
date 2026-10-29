/* Scriptable-compatible API — value types: Color, Font, Size, Point, LinearGradient, Image. */
(function (g) {
  "use strict";

  // ---------- value types ----------
  const hex2 = (n) => Math.round(n).toString(16).padStart(2, "0").toUpperCase();

  class Color {
    constructor(hex, alpha = 1) {
      let h = String(hex).replace("#", "");
      if (h.length === 3) h = h.split("").map((c) => c + c).join("");
      if (h.length === 8) { alpha = parseInt(h.slice(0, 2), 16) / 255; h = h.slice(2); }
      // Scriptable doesn't reject malformed hex strings (scripts pass placeholders like "default"): black.
      if (!/^[0-9a-fA-F]{6}$/.test(h)) h = "000000";
      this.hex = h.toUpperCase();
      this.alpha = alpha;
      this.red = parseInt(h.slice(0, 2), 16) / 255;
      this.green = parseInt(h.slice(2, 4), 16) / 255;
      this.blue = parseInt(h.slice(4, 6), 16) / 255;
    }
    toJSON() {
      const own = `#${hex2(this.alpha * 255)}${this.hex}`;
      return this._dark ? `${own}/${this._dark.toJSON()}` : own;
    }
    /** Light / dark appearance pair; the widget switches with the system theme ("#light/#dark" in the tree). */
    static dynamic(light, dark) {
      const color = new Color(light.hex, light.alpha);
      color._dark = new Color(dark.hex, dark.alpha);
      return color;
    }
  }
  const named = {
    black: "000000", white: "FFFFFF", gray: "8E8E93", lightGray: "C7C7CC", darkGray: "48484A",
    red: "FF3B30", green: "34C759", blue: "007AFF", orange: "FF9500", yellow: "FFCC00",
    purple: "AF52DE", brown: "A2845E", cyan: "32ADE6", magenta: "FF2D55",
  };
  for (const [name, h] of Object.entries(named)) Color[name] = () => new Color(h);
  Color.clear = () => new Color("000000", 0);
  g.Color = Color;

  class Font {
    constructor(name, size) { this.name = name; this.size = size; this.weight = "regular"; this.italic = false; }
    static _w(weight, size, italic = false) { const f = new Font("system", size); f.weight = weight; f.italic = italic; return f; }
  }
  const weights = ["ultraLight", "thin", "light", "regular", "medium", "semibold", "bold", "heavy", "black"];
  for (const w of weights) {
    Font[`${w}SystemFont`] = (size) => Font._w(w, size);
    Font[`${w}RoundedSystemFont`] = (size) => Object.assign(Font._w(w, size), { name: "rounded" });
    Font[`${w}MonospacedSystemFont`] = (size) => Object.assign(Font._w(w, size), { name: "monospace" });
  }
  Font.systemFont = (size) => Font._w("regular", size);
  Font.boldSystemFont = (size) => Font._w("bold", size);
  Font.italicSystemFont = (size) => Font._w("regular", size, true);
  Font.largeTitle = () => Font._w("regular", 34);
  Font.title1 = () => Font._w("regular", 28);
  Font.title2 = () => Font._w("regular", 22);
  Font.title3 = () => Font._w("regular", 20);
  Font.headline = () => Font._w("semibold", 17);
  Font.body = () => Font._w("regular", 17);
  Font.callout = () => Font._w("regular", 16);
  Font.subheadline = () => Font._w("regular", 15);
  Font.footnote = () => Font._w("regular", 13);
  Font.caption1 = () => Font._w("regular", 12);
  Font.caption2 = () => Font._w("regular", 11);
  g.Font = Font;

  class Size { constructor(width, height) { this.width = width; this.height = height; } }
  class Point { constructor(x, y) { this.x = x; this.y = y; } }
  g.Size = Size;
  g.Point = Point;

  class LinearGradient {
    constructor() { this.colors = []; this.locations = null; this.startPoint = new Point(0, 0); this.endPoint = new Point(0, 1); }
    toJSON() {
      return {
        colors: this.colors.map((c) => (c instanceof Color ? c : new Color(c)).toJSON()),
        locations: this.locations, start: [this.startPoint.x, this.startPoint.y], end: [this.endPoint.x, this.endPoint.y],
      };
    }
  }
  g.LinearGradient = LinearGradient;

  /** Image handle backed by a file cached on the native side. */
  class Image {
    constructor(info) { this._src = info.src; this.size = new Size(info.width, info.height); }
  }
  g.Image = Image;
})(globalThis);
