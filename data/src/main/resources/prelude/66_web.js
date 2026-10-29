/* Scriptable-compatible API — WebView (also headless in widgets) and DocumentPicker (app only). */
(function (g) {
  "use strict";

  const web = (op, id, args) => __web(op, id, JSON.stringify(args ?? {}));

  class WebView {
    constructor() { this._id = __webCreate(); this.shouldAllowRequest = null; }
    async loadURL(url) { await web("loadUrl", this._id, { url: String(url) }); }
    async loadRequest(request) { await this.loadURL(request.url); }
    async loadHTML(html, baseURL) { await web("loadHtml", this._id, { html: String(html), baseUrl: baseURL ?? null }); }
    async loadFile(path) { await this.loadHTML(FileManager.local().readString(path) ?? ""); }
    async evaluateJavaScript(javaScript, useCallback = false) {
      const result = await web("eval", this._id, { script: String(javaScript), callback: !!useCallback });
      return result == null ? null : JSON.parse(result);
    }
    async getHTML() { return await web("html", this._id); }
    async present(fullscreen = false) { await web("present", this._id, { fullscreen: !!fullscreen }); }
    /** Loads complete before loadURL()/loadHTML() resolve. */
    async waitForLoad() {}
    static async loadURL(url, preferredSize, fullscreen = false) { const wv = new WebView(); await wv.loadURL(url); await wv.present(fullscreen); }
    static async loadHTML(html, baseURL, preferredSize, fullscreen = false) { const wv = new WebView(); await wv.loadHTML(html, baseURL); await wv.present(fullscreen); }
    static async loadFile(path, preferredSize, fullscreen = false) { const wv = new WebView(); await wv.loadFile(path); await wv.present(fullscreen); }
  }
  g.WebView = WebView;

  // ---------- DocumentPicker ----------
  const UTI = {
    "public.text": "text/*", "public.plain-text": "text/plain", "public.utf8-plain-text": "text/plain",
    "public.json": "application/json", "public.html": "text/html", "public.comma-separated-values-text": "text/csv",
    "public.image": "image/*", "public.png": "image/png", "public.jpeg": "image/jpeg", "com.adobe.pdf": "application/pdf",
    "public.data": "*/*", "public.item": "*/*", "public.content": "*/*",
  };
  const EXT = {
    txt: "text/plain", json: "application/json", html: "text/html", htm: "text/html", csv: "text/csv", js: "text/javascript",
    png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", pdf: "application/pdf",
  };
  const mimeTypes = (types) => (types && types.length ? types.map((t) => UTI[t] ?? (String(t).includes("/") ? String(t) : "*/*")) : ["*/*"]);
  const mimeOf = (name, fallback) => EXT[String(name).split(".").pop().toLowerCase()] ?? fallback;
  const doc = async (op, args) => JSON.parse(await __doc(op, JSON.stringify(args)));

  g.DocumentPicker = {
    async open(types) { return doc("open", { kind: "open", mimeTypes: mimeTypes(types), multiple: true }); },
    async openFile() {
      const paths = await doc("open", { kind: "open", mimeTypes: ["*/*"], multiple: false });
      if (!paths.length) throw new Error("DocumentPicker was cancelled");
      return paths[0];
    },
    async openFolder() { throw new Error("DocumentPicker.openFolder() is not supported on Android"); },
    async export(path) {
      const name = FileManager.local().fileName(path, true);
      return doc("export", { path, name, mimeType: mimeOf(name, "application/octet-stream") });
    },
    async exportString(content, name = "Untitled.txt") {
      return doc("export", { text: String(content), name, mimeType: mimeOf(name, "text/plain") });
    },
    async exportImage(image, name = "Untitled.png") {
      if (!(image instanceof Image)) throw new Error("exportImage() expects an Image");
      return doc("export", { src: image._src, name, mimeType: "image/png" });
    },
    async exportData(data, name = "Untitled") {
      if (!(data instanceof Data)) throw new Error("exportData() expects Data");
      return doc("export", { base64: data.toBase64String(), name, mimeType: mimeOf(name, "application/octet-stream") });
    },
  };
})(globalThis);
