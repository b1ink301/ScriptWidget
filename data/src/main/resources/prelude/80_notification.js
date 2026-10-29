/* Scriptable-compatible API — Notification (local notifications). */
(function (g) {
  "use strict";

  class Notification {
    constructor() {
      this.identifier = `n-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      this.title = ""; this.subtitle = ""; this.body = "";
      this.threadIdentifier = null; this.openURL = null; this.sound = null;
      this.userInfo = {};
      /** Script run (with args.notification) when the notification is tapped and has no openURL. */
      this.scriptName = __env.scriptName ?? null;
      this.deliveryDate = null;
      this.actions = [];
      this._triggerAt = null;
    }
    addAction(title, url, destructive = false) {
      this.actions.push({ title: String(title), url: String(url), destructive: !!destructive });
    }
    static _fromSpec(spec) {
      const out = new Notification();
      out.identifier = spec.identifier; out.title = spec.title ?? ""; out.subtitle = spec.subtitle ?? "";
      out.body = spec.body ?? ""; out.threadIdentifier = spec.threadIdentifier ?? null; out.openURL = spec.openURL ?? null;
      out.scriptName = spec.scriptName ?? null;
      out.userInfo = spec.userInfo ? JSON.parse(spec.userInfo) : {};
      out.actions = (spec.actions ?? []).map((a) => ({ title: a.title, url: a.url, destructive: false }));
      out.nextTriggerDate = spec.triggerAt != null ? new Date(spec.triggerAt) : null;
      if (spec.triggerAt != null) out._triggerAt = spec.triggerAt;
      return out;
    }
    static async allPending() { return JSON.parse(__notify("pending", "")).map(Notification._fromSpec); }
    static async allDelivered() { return JSON.parse(__notify("delivered", "")).map(Notification._fromSpec); }
    /** The notification whose tap started this run (same as args.notification). */
    static current() { return g.args?.notification ?? null; }
    static async resetCurrent() {}
    static _delivered(n) {
      const out = new Notification();
      out.identifier = n.identifier; out.title = n.title ?? ""; out.subtitle = n.subtitle ?? "";
      out.body = n.body ?? ""; out.threadIdentifier = n.threadIdentifier ?? null;
      out.userInfo = n.userInfo ?? {};
      out.deliveryDate = n.deliveredAt != null ? new Date(n.deliveredAt) : null;
      return out;
    }
    setTriggerDate(date) {
      if (!(date instanceof Date)) throw new Error("setTriggerDate() expects a Date");
      this._triggerAt = date.getTime();
    }
    async schedule() {
      __notify("schedule", JSON.stringify({
        identifier: String(this.identifier), title: String(this.title ?? ""), subtitle: this.subtitle || null,
        body: String(this.body ?? ""), threadIdentifier: this.threadIdentifier, openURL: this.openURL,
        triggerAt: this._triggerAt,
        scriptName: this.scriptName == null ? null : String(this.scriptName),
        userInfo: this.userInfo && Object.keys(this.userInfo).length ? JSON.stringify(this.userInfo) : null,
        actions: this.actions.map((a) => ({ title: a.title, url: a.url })),
      }));
    }
    async remove() { __notify("remove", JSON.stringify([String(this.identifier)])); }
    static async removePending(identifiers) { __notify("remove", JSON.stringify(identifiers.map(String))); }
    static async removeDelivered(identifiers) { __notify("remove", JSON.stringify(identifiers.map(String))); }
  }
  g.Notification = Notification;
})(globalThis);
