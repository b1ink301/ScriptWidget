/* Scriptable-compatible API — SFSymbol, drawn with Material Icons (no SF Symbols on Android). */
(function (g) {
  "use strict";

  /** Bitmap scale relative to the point size, so symbols stay sharp on high-density screens. */
  const SCALE = 3;
  const WEIGHTS = ["ultraLight", "thin", "light", "regular", "medium", "semibold", "bold", "heavy", "black"];

  class SFSymbol {
    constructor(name) { this._name = String(name); this._size = 17; this._weight = "regular"; this._image = null; this._key = null; }
    static named(name) { return new SFSymbol(name); }
    applyFont(font) { if (font && font.size) this._size = font.size; }
    get image() {
      const key = `${this._size}|${this._weight}`;
      if (this._image && this._key === key) return this._image;
      const info = JSON.parse(__symbol(this._name, Math.round(this._size * SCALE), this._weight));
      if (info.fallback) console.warn(`SFSymbol "${this._name}" has no Android equivalent; showing a placeholder`);
      this._image = new Image(info);
      this._key = key;
      return this._image;
    }
  }
  for (const w of WEIGHTS) {
    const cap = w[0].toUpperCase() + w.slice(1);
    SFSymbol.prototype[`apply${cap}Weight`] = function () { this._weight = w; };
  }
  g.SFSymbol = SFSymbol;
})(globalThis);
