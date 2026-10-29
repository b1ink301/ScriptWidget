/* Scriptable-compatible API — Rect, Path, DrawContext (recorded here, rendered natively on getImage()). */
(function (g) {
  "use strict";

  const box = (r) => ({ x: r.x, y: r.y, w: r.width, h: r.height });
  const at = (p) => ({ x: p.x, y: p.y });
  const color = (c) => (c instanceof Color ? c : new Color(c)).toJSON();

  class Rect {
    constructor(x, y, width, height) { this.x = x; this.y = y; this.width = width; this.height = height; }
    get minX() { return this.x; }
    get midX() { return this.x + this.width / 2; }
    get maxX() { return this.x + this.width; }
    get minY() { return this.y; }
    get midY() { return this.y + this.height / 2; }
    get maxY() { return this.y + this.height; }
    get origin() { return new Point(this.x, this.y); }
    set origin(p) { this.x = p.x; this.y = p.y; }
    get size() { return new Size(this.width, this.height); }
    set size(s) { this.width = s.width; this.height = s.height; }
  }
  g.Rect = Rect;

  class Path {
    constructor() { this._segments = []; }
    move(point) { this._segments.push({ type: "move", ...at(point) }); }
    addLine(point) { this._segments.push({ type: "line", ...at(point) }); }
    addQuadCurve(point, control) { this._segments.push({ type: "quad", cx: control.x, cy: control.y, ...at(point) }); }
    addCurve(point, control1, control2) {
      this._segments.push({ type: "curve", c1x: control1.x, c1y: control1.y, c2x: control2.x, c2y: control2.y, ...at(point) });
    }
    addRect(rect) { this._segments.push({ type: "rect", rect: box(rect) }); }
    addRoundedRect(rect, cornerWidth, cornerHeight) {
      this._segments.push({ type: "roundedRect", rect: box(rect), rx: cornerWidth, ry: cornerHeight });
    }
    addEllipse(rect) { this._segments.push({ type: "ellipse", rect: box(rect) }); }
    addLines(points) {
      points.forEach((p, i) => (i === 0 ? this.move(p) : this.addLine(p)));
    }
    addRects(rects) { rects.forEach((r) => this.addRect(r)); }
    closeSubpath() { this._segments.push({ type: "close" }); }
  }
  g.Path = Path;

  /** Pixel scale used when respectScreenScale is true. */
  const SCREEN_SCALE = 3;

  class DrawContext {
    constructor() {
      this.size = new Size(200, 200); this.respectScreenScale = false; this.opaque = true;
      this._ops = []; this._path = [];
    }
    _op(op) { this._ops.push(op); }

    setFillColor(c) { this._op({ type: "fillColor", color: color(c) }); }
    setStrokeColor(c) { this._op({ type: "strokeColor", color: color(c) }); }
    setLineWidth(w) { this._op({ type: "lineWidth", width: Number(w) }); }
    setFont(font) { this._op({ type: "font", size: font.size, weight: font.weight ?? "regular" }); }
    setTextColor(c) { this._op({ type: "textColor", color: color(c) }); }
    setTextAlignedLeft() { this._op({ type: "textAlign", align: "left" }); }
    setTextAlignedCenter() { this._op({ type: "textAlign", align: "center" }); }
    setTextAlignedRight() { this._op({ type: "textAlign", align: "right" }); }

    fill(rect) { this.fillRect(rect); }
    fillRect(rect) { this._op({ type: "fillRect", rect: box(rect) }); }
    fillEllipse(rect) { this._op({ type: "fillEllipse", rect: box(rect) }); }
    stroke(rect) { this.strokeRect(rect); }
    strokeRect(rect) { this._op({ type: "strokeRect", rect: box(rect) }); }
    strokeEllipse(rect) { this._op({ type: "strokeEllipse", rect: box(rect) }); }

    addPath(path) {
      if (!(path instanceof Path)) throw new Error("addPath() expects a Path");
      this._path.push(...path._segments);
    }
    fillPath() { if (this._path.length) this._op({ type: "fillPath", segments: this._path }); this._path = []; }
    strokePath() { if (this._path.length) this._op({ type: "strokePath", segments: this._path }); this._path = []; }

    drawText(text, point) { this._op({ type: "text", text: String(text), rect: at(point) }); }
    drawTextInRect(text, rect) { this._op({ type: "text", text: String(text), rect: box(rect) }); }
    drawImageAtPoint(image, point) { this._op({ type: "image", src: image._src, rect: at(point) }); }
    drawImageInRect(image, rect) { this._op({ type: "image", src: image._src, rect: box(rect) }); }

    getImage() {
      const spec = {
        width: this.size.width, height: this.size.height, scale: this.respectScreenScale ? SCREEN_SCALE : 1,
        opaque: !!this.opaque, ops: this._ops,
      };
      return new Image(JSON.parse(__canvas(JSON.stringify(spec))));
    }
  }
  g.DrawContext = DrawContext;
})(globalThis);
