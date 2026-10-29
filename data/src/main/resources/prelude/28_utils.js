/* Scriptable-compatible API — UUID, Timer, RelativeDateTimeFormatter, XMLParser, Safari, URLScheme. */
(function (g) {
  "use strict";

  g.UUID = { string: () => __uuid() };

  // ---------- Timer (backed by setTimeout) ----------
  class Timer {
    constructor() { this.timeInterval = 0; this.repeats = false; this._id = null; this._active = false; }
    schedule(callback) {
      this.invalidate();
      this._active = true;
      const tick = () => {
        if (!this._active) return;
        callback(this);
        if (this.repeats && this._active) this._id = setTimeout(tick, this.timeInterval);
        else this._active = false;
      };
      this._id = setTimeout(tick, this.timeInterval);
    }
    invalidate() { this._active = false; if (this._id != null) clearTimeout(this._id); }
    static schedule(timeInterval, repeats, callback) {
      const t = new Timer();
      t.timeInterval = timeInterval;
      t.repeats = repeats;
      t.schedule(callback);
      return t;
    }
  }
  g.Timer = Timer;

  // ---------- RelativeDateTimeFormatter ----------
  const UNITS = [["year", 31536000], ["month", 2592000], ["week", 604800], ["day", 86400], ["hour", 3600], ["minute", 60], ["second", 1]];
  const KO = { year: "년", month: "개월", week: "주", day: "일", hour: "시간", minute: "분", second: "초" };
  const phrases = {
    en: {
      now: "now", yesterday: "yesterday", tomorrow: "tomorrow",
      past: (n, u) => `${n} ${u}${n === 1 ? "" : "s"} ago`, future: (n, u) => `in ${n} ${u}${n === 1 ? "" : "s"}`,
    },
    ko: {
      now: "지금", yesterday: "어제", tomorrow: "내일",
      past: (n, u) => `${n}${KO[u]} 전`, future: (n, u) => `${n}${KO[u]} 후`,
    },
  };

  class RelativeDateTimeFormatter {
    constructor() { this.locale = __env.locale; this._named = false; }
    string(date, referenceDate = new Date()) {
      const lang = String(this.locale || "en").split(/[_-]/)[0];
      const p = phrases[lang] ?? phrases.en;
      const seconds = (date.getTime() - referenceDate.getTime()) / 1000;
      const abs = Math.abs(seconds);
      if (this._named) {
        if (abs < 60) return p.now;
        if (Math.round(seconds / 86400) === -1 && abs >= 86400 * 0.5) return p.yesterday;
        if (Math.round(seconds / 86400) === 1 && abs >= 86400 * 0.5) return p.tomorrow;
      }
      const [unit, size] = UNITS.find(([, s]) => abs >= s) ?? ["second", 1];
      const n = Math.round(abs / size);
      return seconds < 0 ? p.past(n, unit) : p.future(n, unit);
    }
    useNamedDateTimeStyle() { this._named = true; }
    useNumericDateTimeStyle() { this._named = false; }
    useFullUnitsStyle() {}
    useShortUnitsStyle() {}
    useAbbreviatedUnitsStyle() {}
    useSpellOutUnitsStyle() {}
  }
  g.RelativeDateTimeFormatter = RelativeDateTimeFormatter;

  // ---------- XMLParser (SAX events from the native parser) ----------
  class XMLParser {
    constructor(string) {
      this.string = String(string);
      this.didStartDocument = () => {}; this.didEndDocument = () => {};
      this.didStartElement = () => {}; this.didEndElement = () => {};
      this.foundCharacters = () => {}; this.parseErrorOccurred = () => {};
    }
    parse() {
      for (const [type, a, b] of JSON.parse(__xml(this.string))) {
        switch (type) {
          case "startDoc": this.didStartDocument(); break;
          case "start": this.didStartElement(a, b); break;
          case "text": this.foundCharacters(a); break;
          case "end": this.didEndElement(a); break;
          case "endDoc": this.didEndDocument(); break;
          case "error": this.parseErrorOccurred(a); return false;
        }
      }
      return true;
    }
  }
  g.XMLParser = XMLParser;

  // ---------- Safari / URLScheme ----------
  g.Safari = {
    open(url) { __openUrl(String(url)); },
    async openInApp(url, fullscreen) { __openUrl(String(url)); },
  };

  const link = (action) => `scriptwidget://${action}?scriptName=${encodeURIComponent(__env.scriptName ?? "")}`;
  g.URLScheme = {
    forOpeningApp: () => "scriptwidget://",
    forRunningScript: () => link("run"),
    forOpeningScript: () => link("open"),
    forOpeningScriptSettings: () => link("settings"),
    allParameters: () => ({ ...(__env.queryParameters ?? {}) }),
    parameter: (name) => (__env.queryParameters ?? {})[name] ?? null,
  };
})(globalThis);
