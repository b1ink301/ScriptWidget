/* Scriptable-compatible API — console, timers and the script environment (config, args). */
(function (g) {
  "use strict";

  // ---------- console ----------
  const fmt = (v) => {
    if (typeof v === "string") return v;
    if (v instanceof Error) return `${v.name}: ${v.message}`;
    try { return JSON.stringify(v); } catch (e) { return String(v); }
  };
  const log = (level) => (...a) => __log(level, a.map(fmt).join(" "));
  g.console = { log: log("log"), info: log("log"), warn: log("warn"), error: log("error"), debug: log("log") };
  g.log = g.console.log;
  g.logWarning = g.console.warn;
  g.logError = g.console.error;

  // ---------- timers ----------
  let timerSeq = 0;
  const timers = new Set();
  g.setTimeout = (fn, ms = 0, ...a) => {
    const id = ++timerSeq;
    timers.add(id);
    __sleep(Math.max(0, Number(ms) || 0)).then(() => {
      if (timers.delete(id)) fn(...a);
    });
    return id;
  };
  g.clearTimeout = (id) => { timers.delete(id); };
  g.sleep = (ms) => __sleep(ms);

  // ---------- environment ----------
  const source = __env.source ?? (__env.runsInWidget ? "Widget" : "App");
  g.config = {
    runsInWidget: source === "Widget",
    runsInApp: source === "App",
    runsInActionExtension: source === "ShareSheet",
    runsInNotification: source === "Notification",
    runsFromHomeScreen: source === "HomeScreen",
    /** Android extension: started by an automation app (Tasker, MacroDroid, …). */
    runsFromAutomation: source === "Automation",
    runsWithSiri: false,
    runsInAccessoryWidget: false,
    widgetFamily: __env.widgetFamily,
  };
  const share = __env.share ?? {};
  g.args = {
    widgetParameter: __env.widgetParameter ?? null,
    queryParameters: __env.queryParameters ?? {},
    /** Android extension: the tapAction of the widget element that started this run. */
    widgetAction: __env.widgetAction ?? null,
    shortcutParameter: __env.shortcutParameter ?? null,
    siriShortcutArguments: {},
    plainTexts: share.plainTexts ?? [],
    urls: share.urls ?? [],
    fileURLs: share.fileURLs ?? [],
    // Images, all and notification need classes defined later in the prelude (see 90_script.js).
    images: [],
    all: [],
    notification: null,
  };
})(globalThis);
