/* Scriptable-compatible API — in-app UI: Alert, TextField, UITable(Row/Cell), DatePicker, QuickLook. */
(function (g) {
  "use strict";

  const ui = async (op, spec) => __ui(op, JSON.stringify(spec ?? {}));
  const col = (c) => (c == null ? null : (c instanceof Color ? c : new Color(c)).toJSON());

  // ---------- TextField / Alert ----------
  const KEYBOARDS = ["Default", "NumberPad", "DecimalPad", "NumbersAndPunctuation", "PhonePad", "WebSearch",
    "EmailAddress", "URL", "Twitter"];

  class TextField {
    constructor(placeholder, text, secure) {
      this.placeholder = placeholder ?? ""; this.text = text ?? ""; this.isSecure = !!secure;
      this.textColor = null; this.font = null; this._keyboard = "default"; this._align = "left";
    }
    leftAlignText() { this._align = "left"; }
    centerAlignText() { this._align = "center"; }
    rightAlignText() { this._align = "right"; }
  }
  for (const k of KEYBOARDS) TextField.prototype[`set${k}Keyboard`] = function () { this._keyboard = k; };
  g.TextField = TextField;

  class Alert {
    constructor() { this.title = null; this.message = null; this._actions = []; this._fields = []; this._values = null; }
    addAction(title) { this._actions.push({ title: String(title), role: "default" }); }
    addDestructiveAction(title) { this._actions.push({ title: String(title), role: "destructive" }); }
    addCancelAction(title) { this._actions.push({ title: String(title), role: "cancel" }); }
    addTextField(placeholder, text) { const f = new TextField(placeholder, text, false); this._fields.push(f); return f; }
    addSecureTextField(placeholder, text) { const f = new TextField(placeholder, text, true); this._fields.push(f); return f; }
    /** Scriptable scripts often call textFieldValue() without an index: that's the first field. */
    textFieldValue(index = 0) {
      if (this._values && index < this._values.length) return this._values[index];
      return this._fields[index]?.text ?? "";
    }
    async _show(style) {
      const r = JSON.parse(await ui("alert", {
        title: this.title, message: this.message, style, actions: this._actions,
        textFields: this._fields.map((f) => ({ placeholder: String(f.placeholder), text: String(f.text), secure: f.isSecure })),
      }));
      this._values = r.texts ?? [];
      return r.index;
    }
    present() { return this._show("alert"); }
    presentAlert() { return this._show("alert"); }
    presentSheet() { return this._show("sheet"); }
  }
  g.Alert = Alert;

  // ---------- UITable ----------
  class UITableCell {
    constructor(type) {
      this._type = type; this.title = null; this.subtitle = null; this.titleColor = null; this.subtitleColor = null;
      this.titleFont = null; this.subtitleFont = null; this.widthWeight = null; this.onTap = null; this.dismissOnTap = false;
      this._image = null; this._url = null; this._align = "left";
    }
    static text(title, subtitle) { const c = new UITableCell("text"); c.title = title ?? null; c.subtitle = subtitle ?? null; return c; }
    static image(image) {
      if (!(image instanceof Image)) throw new Error("UITableCell.image() expects an Image");
      const c = new UITableCell("image"); c._image = image; return c;
    }
    /** Loaded (once) when the table is presented; a failed download shows an empty cell. */
    static imageAtURL(url) { const c = new UITableCell("image"); c._url = String(url); return c; }
    static button(title) { const c = new UITableCell("button"); c.title = title; return c; }
    leftAligned() { this._align = "left"; }
    centerAligned() { this._align = "center"; }
    rightAligned() { this._align = "right"; }
    _spec() {
      return {
        type: this._type, title: this.title == null ? null : String(this.title),
        subtitle: this.subtitle == null ? null : String(this.subtitle),
        titleColor: col(this.titleColor), subtitleColor: col(this.subtitleColor),
        src: this._image ? this._image._src : null, align: this._align, widthWeight: this.widthWeight,
      };
    }
  }
  g.UITableCell = UITableCell;

  class UITableRow {
    constructor() {
      this.cells = []; this.height = null; this.isHeader = false; this.dismissOnSelect = true;
      this.onSelect = null; this.backgroundColor = null; this.cellSpacing = 0;
    }
    addCell(cell) { this.cells.push(cell); return cell; }
    addText(title, subtitle) { return this.addCell(UITableCell.text(title, subtitle)); }
    addImage(image) { return this.addCell(UITableCell.image(image)); }
    addButton(title) { return this.addCell(UITableCell.button(title)); }
    _spec() {
      return {
        cells: this.cells.map((c) => c._spec()), height: this.height, isHeader: !!this.isHeader,
        backgroundColor: col(this.backgroundColor), selectable: typeof this.onSelect === "function",
      };
    }
  }
  g.UITableRow = UITableRow;

  class UITable {
    constructor() { this._rows = []; this.showSeparators = false; }
    addRow(row) { this._rows.push(row); }
    removeRow(row) { this._rows = this._rows.filter((r) => r !== row); }
    removeAllRows() { this._rows = []; }
    /** The table is re-sent after every handler, so reload() has nothing to do. */
    reload() {}
    async _loadImages() {
      for (const row of this._rows) for (const cell of row.cells) {
        if (!cell._url || cell._image) continue;
        try { cell._image = await new Request(cell._url).loadImage(); } catch (e) { cell._url = null; }
      }
    }
    async present(fullscreen = false) {
      for (;;) {
        await this._loadImages();
        const spec = { rows: this._rows.map((r) => r._spec()), showSeparators: !!this.showSeparators, fullscreen: !!fullscreen };
        const ev = JSON.parse(await ui("table", spec));
        if (ev.type === "dismissed") return;
        if (ev.type === "selected") {
          const row = this._rows[ev.row];
          if (row && typeof row.onSelect === "function") await row.onSelect(ev.row);
          if (row && row.dismissOnSelect) { await ui("tableDismiss"); return; }
        } else if (ev.type === "tapped") {
          const cell = this._rows[ev.row]?.cells[ev.cell];
          if (cell && typeof cell.onTap === "function") await cell.onTap();
          if (cell && cell.dismissOnTap) { await ui("tableDismiss"); return; }
        }
      }
    }
  }
  g.UITable = UITable;

  // ---------- DatePicker ----------
  const time = (d) => (d instanceof Date ? d.getTime() : null);

  class DatePicker {
    constructor() { this.initialDate = null; this.minimumDate = null; this.maximumDate = null; this.countdownDuration = 0; this.minuteInterval = 1; }
    async _pick(mode, extra = {}) {
      const t = await ui("date", { mode, initial: time(this.initialDate), min: time(this.minimumDate), max: time(this.maximumDate), ...extra });
      if (t == null) throw new Error("DatePicker was cancelled");
      return t;
    }
    async pickDate() { return new Date(await this._pick("date")); }
    async pickTime() { return new Date(await this._pick("time")); }
    async pickDateAndTime() { return new Date(await this._pick("dateAndTime")); }
    /** Resolves to the duration in seconds. */
    async pickCountdownDuration() {
      return (await this._pick("countdown", { countdownSeconds: Math.round(this.countdownDuration || 0) })) / 1000;
    }
  }
  g.DatePicker = DatePicker;

  // ---------- QuickLook ----------
  g.QuickLook = {
    async present(item, fullscreen = false) {
      let spec;
      if (item instanceof Image) spec = { src: item._src };
      else if (typeof item === "string") spec = { text: item };
      else if (typeof Data !== "undefined" && item instanceof Data) spec = { text: item.toRawString() };
      else spec = { text: JSON.stringify(item, null, 2) };
      await ui("quickLook", { ...spec, fullscreen: !!fullscreen });
    },
  };
})(globalThis);
