/* Scriptable-compatible API — Pasteboard, ShareSheet, Mail, Message, CallbackURL, Speech, Dictation. */
(function (g) {
  "use strict";

  const compose = (kind, spec) => __compose(kind, JSON.stringify(spec));

  // ---------- Pasteboard ----------
  g.Pasteboard = {
    copy(string) { __clipboard("copy", String(string)); },
    copyString(string) { __clipboard("copy", String(string)); },
    paste() { return __clipboard("paste"); },
    pasteString() { return __clipboard("paste"); },
    copyImage() { throw new Error("Pasteboard.copyImage() is not supported on Android"); },
    pasteImage() { return null; },
  };

  // ---------- ShareSheet ----------
  const isSandboxFile = (value) => {
    try { return FileManager.local().fileExists(value) && !FileManager.local().isDirectory(value); } catch (e) { return false; }
  };
  const attachData = (data, filename) => {
    const fm = FileManager.local();
    const path = fm.joinPath(fm.temporaryDirectory(), `attachments/${filename}`);
    fm.write(path, data);
    return path;
  };

  g.ShareSheet = {
    async present(activityItems) {
      const texts = [], files = [];
      for (const item of activityItems ?? []) {
        if (item instanceof Image) files.push(item._src);
        else if (item instanceof Data) files.push(attachData(item, `shared-${Date.now()}.bin`));
        else if (typeof item === "string" && isSandboxFile(item)) files.push(item);
        else if (item != null) texts.push(typeof item === "object" ? JSON.stringify(item) : String(item));
      }
      await compose("share", { texts, files });
      // Android doesn't report what the user did in the share sheet.
      return { completed: true, activity_type: null };
    },
  };

  // ---------- Mail / Message ----------
  class Attachments {
    constructor() { this._attachments = []; }
    addImageAttachment(image) {
      if (!(image instanceof Image)) throw new Error("addImageAttachment() expects an Image");
      this._attachments.push(image._src);
    }
    addFileAttachment(path) { this._attachments.push(String(path)); }
    addDataAttachment(data, mimeType, filename) {
      if (!(data instanceof Data)) throw new Error("addDataAttachment() expects Data");
      this._attachments.push(attachData(data, filename ?? "attachment"));
    }
  }

  class Mail extends Attachments {
    constructor() {
      super();
      this.toRecipients = []; this.ccRecipients = []; this.bccRecipients = [];
      this.subject = ""; this.body = ""; this.isBodyHTML = false; this.preferredSendingEmailAddress = null;
    }
    async send() {
      await compose("mail", {
        to: this.toRecipients.map(String), cc: this.ccRecipients.map(String), bcc: this.bccRecipients.map(String),
        subject: String(this.subject ?? ""), body: String(this.body ?? ""), html: !!this.isBodyHTML, attachments: this._attachments,
      });
    }
  }
  g.Mail = Mail;

  class Message extends Attachments {
    constructor() { super(); this.recipients = []; this.body = ""; }
    async send() {
      await compose("message", { recipients: this.recipients.map(String), body: String(this.body ?? ""), attachments: this._attachments });
    }
  }
  g.Message = Message;

  // ---------- CallbackURL (x-callback-url) ----------
  class CallbackURL {
    constructor(baseURL) { this.baseURL = String(baseURL); this._params = []; }
    addParameter(name, value) { this._params.push([String(name), String(value)]); }
    _url(extra = []) {
      const query = [...this._params, ...extra].map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join("&");
      return query ? `${this.baseURL}${this.baseURL.includes("?") ? "&" : "?"}${query}` : this.baseURL;
    }
    getURL() { return this._url(); }
    async open() {
      const id = UUID.string();
      const back = (status) => `scriptwidget://x-callback-url/${status}?requestId=${id}`;
      const url = this._url([["x-success", back("success")], ["x-error", back("error")], ["x-cancel", back("cancel")]]);
      const result = JSON.parse(await __callback(url, id));
      if (result.status === "success") return result.params;
      if (result.status === "cancel") throw new Error("x-callback-url was cancelled");
      throw new Error(result.params.errorMessage ?? result.params.error ?? "x-callback-url failed");
    }
  }
  g.CallbackURL = CallbackURL;

  // ---------- Speech / Dictation ----------
  g.Speech = { async speak(text) { await __speak(String(text)); } };

  g.Dictation = {
    async start(locale) {
      const text = await __ui("dictate", JSON.stringify({ locale: locale ?? null }));
      if (text == null) throw new Error("Dictation was cancelled");
      return text;
    },
  };
})(globalThis);
