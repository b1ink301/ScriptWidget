/* Locale formatting backed by java.text/java.time: Intl (subset), toLocale*String(), DateFormatter. */
(function (g) {
  "use strict";

  const json = (o) => JSON.stringify(o, (k, v) => (v === undefined ? null : v));
  const localeTag = (l) => (Array.isArray(l) ? l[0] : l) ?? null;

  const defaults = () => JSON.parse(__fmt("defaults", "{}"));

  // Intl constructors may be called without `new`, like in browsers.
  function NumberFormat(locale, options = {}) {
    if (!(this instanceof NumberFormat)) return new NumberFormat(locale, options);
    this.locale = localeTag(locale); this.options = options || {};
  }
  NumberFormat.prototype.format = function (value) {
    const o = this.options;
    return __fmt("number", json({
      value: Number(value), locale: this.locale, style: o.style ?? "decimal", currency: o.currency,
      minimumFractionDigits: o.minimumFractionDigits, maximumFractionDigits: o.maximumFractionDigits,
      useGrouping: o.useGrouping !== false,
    }));
  };
  NumberFormat.prototype.resolvedOptions = function () {
    return { locale: this.locale ?? defaults().locale, ...this.options };
  };

  const COMPONENTS = ["weekday", "year", "month", "day", "hour", "minute", "second"];

  /**
   * A java.time pattern for component options ({ month: "long", day: "numeric", … }), ordered like
   * browsers do for Korean/Japanese/Chinese and for English (other languages use the English order).
   */
  function componentPattern(locale, o) {
    const lang = String(locale ?? defaults().locale).toLowerCase().split(/[-_]/)[0];
    const cjk = lang === "ko" || lang === "ja" || lang === "zh";
    const ko = lang === "ko";
    const textMonth = o.month === "short" || o.month === "long" || o.month === "narrow";
    const M = { numeric: "M", "2-digit": "MM", short: "MMM", long: "MMMM", narrow: "MMMMM" }[o.month];
    const y = o.year === "2-digit" ? "yy" : o.year ? "y" : null;
    const d = o.day === "2-digit" ? "dd" : o.day ? "d" : null;
    const E = { short: "E", long: "EEEE", narrow: "EEEEE" }[o.weekday];

    let date = "";
    if (cjk) {
      const unit = (v, u) => (v ? v + (ko ? u : "") : null);
      const parts = textMonth || !(o.month && o.day && !o.year)
        ? [unit(y, ko ? "'년'" : ""), M && (textMonth || !ko ? M : M + "'월'"), unit(d, ko ? "'일'" : "")]
        : [M + ".", d + "."];
      date = parts.filter(Boolean).join(" ");
      if (E) date = date ? (o.weekday === "long" ? `${date} ${E}` : `${date} (${E})`) : E;
    } else {
      const md = textMonth ? [M, d].filter(Boolean).join(" ") : [M, d].filter(Boolean).join("/");
      date = textMonth ? [md, y].filter(Boolean).join(", ") : [md, y].filter(Boolean).join("/");
      if (E) date = date ? `${E}, ${date}` : E;
    }

    let time = "";
    if (o.hour || o.minute || o.second) {
      const h12 = o.hour12 ?? (o.hourCycle ? o.hourCycle === "h12" || o.hourCycle === "h11" : lang === "ko" || lang === "en");
      const h = o.hour ? (h12 ? (o.hour === "2-digit" ? "hh" : "h") : (o.hour === "2-digit" ? "HH" : "H")) : null;
      const hms = [h, o.minute ? "mm" : null, o.second ? "ss" : null].filter(Boolean).join(":");
      time = h && h12 ? (cjk ? `a ${hms}` : `${hms} a`) : hms;
    }
    return [date, time].filter(Boolean).join(cjk ? " " : ", ");
  }

  /** dateStyle / timeStyle, or component options (weekday, year, month, day, hour, minute, second). */
  function DateTimeFormat(locale, options = {}) {
    if (!(this instanceof DateTimeFormat)) return new DateTimeFormat(locale, options);
    this.locale = localeTag(locale); this.options = options || {};
  }
  DateTimeFormat.prototype.format = function (date) {
    const o = this.options;
    const d = date instanceof Date ? date : new Date(date ?? Date.now());
    const components = COMPONENTS.some((k) => o[k] != null);
    return __fmt("date", json({
      time: d.getTime(), locale: this.locale, timeZone: o.timeZone,
      pattern: components && !o.dateStyle && !o.timeStyle ? componentPattern(this.locale, o) : undefined,
      dateStyle: o.dateStyle, timeStyle: o.timeStyle,
    }));
  };
  DateTimeFormat.prototype.resolvedOptions = function () {
    const base = defaults();
    return { ...this.options, locale: this.locale ?? base.locale, timeZone: this.options.timeZone ?? base.timeZone };
  };

  if (typeof g.Intl === "undefined") g.Intl = { NumberFormat, DateTimeFormat };

  Number.prototype.toLocaleString = function (locale, options) { return new NumberFormat(locale, options).format(this); };
  const hasComponents = (o) => COMPONENTS.some((k) => o?.[k] != null);
  Date.prototype.toLocaleDateString = function (locale, options = {}) {
    const o = hasComponents(options) ? options : { dateStyle: "short", ...options, timeStyle: undefined };
    return new DateTimeFormat(locale, o).format(this);
  };
  Date.prototype.toLocaleTimeString = function (locale, options = {}) {
    const o = hasComponents(options) ? options : { timeStyle: "medium", ...options, dateStyle: undefined };
    return new DateTimeFormat(locale, o).format(this);
  };
  Date.prototype.toLocaleString = function (locale, options = {}) {
    const o = hasComponents(options) ? options : { dateStyle: "short", timeStyle: "medium", ...options };
    return new DateTimeFormat(locale, o).format(this);
  };

  const STYLES = ["No", "Short", "Medium", "Long", "Full"];

  class DateFormatter {
    constructor() { this.dateFormat = null; this.locale = null; this._dateStyle = "medium"; this._timeStyle = "short"; }
    string(date) {
      const d = date instanceof Date ? date : new Date(date);
      const spec = { time: d.getTime(), locale: this.locale };
      if (this.dateFormat) return __fmt("date", json({ ...spec, pattern: this.dateFormat }));
      if (!this._dateStyle && !this._timeStyle) return "";
      return __fmt("date", json({ ...spec, dateStyle: this._dateStyle, timeStyle: this._timeStyle }));
    }
    /** Returns null when the string doesn't match `dateFormat`. */
    date(text) {
      if (!this.dateFormat) throw new Error("DateFormatter.date() requires dateFormat");
      const t = __fmt("parse", json({ text: String(text), pattern: this.dateFormat, locale: this.locale }));
      return t == null ? null : new Date(t);
    }
  }
  for (const s of STYLES) {
    const value = s === "No" ? null : s.toLowerCase();
    DateFormatter.prototype[`use${s}DateStyle`] = function () { this._dateStyle = value; };
    DateFormatter.prototype[`use${s}TimeStyle`] = function () { this._timeStyle = value; };
  }
  g.DateFormatter = DateFormatter;
})(globalThis);
