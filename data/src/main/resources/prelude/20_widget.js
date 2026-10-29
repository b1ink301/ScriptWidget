/* Scriptable-compatible API — widget elements: ListWidget, WidgetStack, WidgetText, WidgetDate, WidgetImage, WidgetSpacer. */
(function (g) {
  "use strict";

  // ---------- widget elements ----------
  const pad = (t, l, b, r) => [t, l, b, r].map((n) => Number(n) || 0);

  class WidgetText {
    constructor(text) {
      this.text = String(text);
      this.textColor = null;
      this.font = null;
      this.textOpacity = 1;
      this.lineLimit = 0;
      this.minimumScaleFactor = 1;
      this.shadowColor = null;
      this.shadowRadius = 0;
      this.shadowOffset = null;
      this.url = null;
      this._align = "left";
    }
    leftAlignText() { this._align = "left"; }
    centerAlignText() { this._align = "center"; }
    rightAlignText() { this._align = "right"; }
    toJSON() {
      return {
        type: "text", text: this.text, color: this.textColor,
        size: this.font ? this.font.size : 17, weight: this.font ? this.font.weight : "regular",
        italic: this.font ? this.font.italic : false, lineLimit: this.lineLimit,
        align: this._align, opacity: this.textOpacity, url: this.url, action: this.tapAction ?? null,
        font: this.font ? String(this.font.name ?? "system") : "system",
        fontFile: this.font ? fontFile(this.font.name) : null,
        shadowColor: this.shadowColor, shadowRadius: Number(this.shadowRadius) || 0,
        shadowOffset: this.shadowOffset ? [Number(this.shadowOffset.x) || 0, Number(this.shadowOffset.y) || 0] : [],
        minimumScaleFactor: Math.min(1, Math.max(0.1, Number(this.minimumScaleFactor) || 1)),
      };
    }
  }

  /** Fonts the script put in Documents/fonts/<name>.ttf|.otf (Android has no installable fonts). */
  const SYSTEM_FONTS = new Set(["system", "monospace", "rounded"]);
  function fontFile(name) {
    if (name == null || SYSTEM_FONTS.has(String(name))) return null;
    const fm = FileManager.local();
    for (const ext of [".ttf", ".otf"]) {
      const path = fm.joinPath(fm.documentsDirectory(), "fonts/" + name + ext);
      if (fm.fileExists(path)) return path;
    }
    return null;
  }

  class WidgetDate {
    constructor(date) {
      this.date = date; this.textColor = null; this.font = null; this._style = "time"; this._align = "left";
    }
    applyTimeStyle() { this._style = "time"; }
    applyDateStyle() { this._style = "date"; }
    applyRelativeStyle() { this._style = "relative"; }
    applyOffsetStyle() { this._style = "relative"; }
    applyTimerStyle() { this._style = "timer"; }
    leftAlignText() { this._align = "left"; }
    centerAlignText() { this._align = "center"; }
    rightAlignText() { this._align = "right"; }
    toJSON() {
      return {
        type: "date", time: this.date.getTime(), style: this._style, color: this.textColor,
        size: this.font ? this.font.size : 17, weight: this.font ? this.font.weight : "regular", align: this._align,
        action: this.tapAction ?? null,
      };
    }
  }

  class WidgetImage {
    constructor(image) {
      if (!(image instanceof Image)) throw new Error("addImage() expects an Image (e.g. from Request.loadImage())");
      this.image = image; this.imageSize = null; this.cornerRadius = 0; this.imageOpacity = 1; this.url = null;
      this.tintColor = null; this.resizable = true; this.containerRelativeShape = false;
      this.borderColor = null; this.borderWidth = 0;
      this._align = "left"; this._contentMode = "fit";
    }
    leftAlignImage() { this._align = "left"; }
    centerAlignImage() { this._align = "center"; }
    rightAlignImage() { this._align = "right"; }
    applyFittingContentMode() { this._contentMode = "fit"; }
    applyFillingContentMode() { this._contentMode = "fill"; }
    toJSON() {
      return {
        type: "image", src: this.image._src, width: this.image.size.width, height: this.image.size.height,
        size: this.imageSize ? [this.imageSize.width, this.imageSize.height] : null,
        cornerRadius: this.cornerRadius, opacity: this.imageOpacity, url: this.url,
        align: this._align, tint: this.tintColor, contentMode: this._contentMode, action: this.tapAction ?? null,
      };
    }
  }

  class WidgetSpacer {
    constructor(length) { this.length = length == null ? null : Number(length); }
    toJSON() { return { type: "spacer", length: this.length }; }
  }

  class Container {
    constructor() {
      this._children = []; this.backgroundColor = null; this.backgroundGradient = null; this.backgroundImage = null;
      this.spacing = 0; this.url = null; this._padding = null;
    }
    _background() {
      const gr = this.backgroundGradient, img = this.backgroundImage;
      if (gr != null && !(gr instanceof LinearGradient)) throw new Error("backgroundGradient must be a LinearGradient");
      if (img != null && !(img instanceof Image)) throw new Error("backgroundImage must be an Image");
      return { bg: this.backgroundColor, gradient: gr, backgroundImage: img ? img._src : null };
    }
    addText(text) { return this._add(new WidgetText(text)); }
    addDate(date) { return this._add(new WidgetDate(date)); }
    addImage(image) { return this._add(new WidgetImage(image)); }
    addSpacer(length) { return this._add(new WidgetSpacer(length)); }
    addStack() { return this._add(new WidgetStack()); }
    /** Android extension: a tappable pill that re-runs the script with args.widgetAction = action. */
    addButton(title, action) {
      const button = this.addStack();
      button.tapAction = String(action);
      button.setPadding(6, 12, 6, 12);
      button.cornerRadius = 12;
      button.backgroundColor = new Color("#FFFFFF", 0.18);
      const label = button.addText(String(title));
      label.font = Font.semiboldSystemFont(14);
      label.textColor = Color.white();
      return button;
    }
    setPadding(top, leading, bottom, trailing) { this._padding = pad(top, leading, bottom, trailing); }
    useDefaultPadding() { this._padding = null; }
    _add(el) { this._children.push(el); return el; }
  }

  class WidgetStack extends Container {
    constructor() {
      super(); this._dir = "h"; this._align = "center"; this.cornerRadius = 0; this.size = null;
      this.borderColor = null; this.borderWidth = 0;
    }
    layoutHorizontally() { this._dir = "h"; }
    layoutVertically() { this._dir = "v"; }
    topAlignContent() { this._align = "top"; }
    centerAlignContent() { this._align = "center"; }
    bottomAlignContent() { this._align = "bottom"; }
    toJSON() {
      return {
        type: "stack", dir: this._dir, align: this._align, ...this._background(), padding: this._padding,
        spacing: this.spacing, cornerRadius: this.cornerRadius, borderColor: this.borderColor, borderWidth: this.borderWidth,
        size: this.size ? [this.size.width, this.size.height] : null, url: this.url, action: this.tapAction ?? null,
        children: this._children,
      };
    }
  }

  class ListWidget extends Container {
    constructor() { super(); this.refreshAfterDate = null; }
    async presentSmall() { Script.setWidget(this); }
    async presentMedium() { Script.setWidget(this); }
    async presentLarge() { Script.setWidget(this); }
    toJSON() {
      const r = this.refreshAfterDate;
      if (r != null && !(r instanceof Date)) throw new Error("refreshAfterDate must be a Date");
      return {
        type: "widget", ...this._background(), padding: this._padding, spacing: this.spacing, url: this.url,
        refreshAfter: r ? r.getTime() : null, action: this.tapAction ?? null, children: this._children,
      };
    }
  }
  g.ListWidget = ListWidget;
  g.WidgetStack = WidgetStack;
  // Element types are returned by addText() etc.; exposed like in Scriptable (instanceof, API reference).
  g.WidgetText = WidgetText;
  g.WidgetDate = WidgetDate;
  g.WidgetImage = WidgetImage;
  g.WidgetSpacer = WidgetSpacer;
})(globalThis);
