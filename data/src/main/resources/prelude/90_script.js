/* Scriptable-compatible API — Script. */
(function (g) {
  "use strict";

  // ---------- Script ----------
  g.Script = {
    _done: false,
    setWidget(widget) {
      if (!(widget instanceof ListWidget)) throw new Error("Script.setWidget() expects a ListWidget");
      __setWidget(JSON.stringify(widget));
    },
    complete() { this._done = true; },
    name() { return __env.scriptName ?? "script"; },
    /** Value returned to the automation app (Tasker, …) that ran the script. */
    setShortcutOutput(value) { __setOutput(JSON.stringify(value ?? null)); },
  };

  // ---------- args that need Image / Notification ----------
  const share = __env.share ?? {};
  g.args.images = (share.images ?? []).map((path) => Image.fromFile(path)).filter((image) => image != null);
  g.args.all = [...g.args.plainTexts, ...g.args.urls, ...g.args.fileURLs, ...g.args.images];
  if (__env.notification) g.args.notification = Notification._delivered(__env.notification);
})(globalThis);
