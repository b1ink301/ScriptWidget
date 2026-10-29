/* Scriptable-compatible API — networking: Request. */
(function (g) {
  "use strict";

  // ---------- networking ----------
  class Request {
    constructor(url) {
      this.url = url; this.method = "GET"; this.headers = {}; this.body = null; this.timeoutInterval = 60; this.response = null;
    }
    _spec() {
      let body = this.body;
      if (body != null && typeof body !== "string") {
        body = JSON.stringify(body);
        if (!Object.keys(this.headers).some((k) => k.toLowerCase() === "content-type")) {
          this.headers["Content-Type"] = "application/json";
        }
      }
      return JSON.stringify({ url: String(this.url), method: this.method, headers: this.headers, body, timeout: this.timeoutInterval });
    }
    async loadString() {
      const res = JSON.parse(await __http(this._spec()));
      this.response = { url: res.url, statusCode: res.statusCode, headers: res.headers, mimeType: res.mimeType };
      return res.body;
    }
    async loadJSON() {
      const text = await this.loadString();
      try { return JSON.parse(text); } catch (e) {
        throw new Error(`loadJSON(): response is not valid JSON (HTTP ${this.response.statusCode})`);
      }
    }
    async loadImage() { return new Image(JSON.parse(await __loadImage(this._spec()))); }
  }
  g.Request = Request;
})(globalThis);
