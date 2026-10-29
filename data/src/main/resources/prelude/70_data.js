/* Scriptable-compatible API — Data (bytes, held as base64) and its integrations. */
(function (g) {
  "use strict";

  class Data {
    constructor(base64) { this._b64 = base64; }
    static fromString(string) { return new Data(__data("fromString", String(string))); }
    static fromBase64String(base64) { return new Data(__data("validate", String(base64))); }
    static fromBytes(bytes) { return new Data(__data("fromBytes", JSON.stringify(Array.from(bytes)))); }
    static fromFile(path) { return FileManager.local().read(path); }
    static fromPNG(image) { return Data._fromImage(image); }
    /** Cached images are PNG files; the bytes are returned as they are. */
    static fromJPEG(image) { return Data._fromImage(image); }
    static _fromImage(image) {
      if (!(image instanceof Image)) throw new Error("Expected an Image");
      return new Data(__data("fromImage", image._src));
    }
    toRawString() { return __data("toString", this._b64); }
    toBase64String() { return this._b64; }
    getBytes() { return JSON.parse(__data("toBytes", this._b64)); }
  }
  g.Data = Data;

  Image.fromData = (data) => {
    if (!(data instanceof Data)) throw new Error("Image.fromData() expects Data");
    return new Image(JSON.parse(__data("toImage", data._b64)));
  };
  Image.fromFile = (path) => FileManager.local().readImage(path);

  Request.prototype.load = async function () { return new Data(await __httpBytes(this._spec())); };

  FileManager.prototype.read = function (path) {
    const b64 = __fs("readBase64", path);
    return b64 == null ? null : new Data(b64);
  };
  FileManager.prototype.write = function (path, data) {
    if (!(data instanceof Data)) throw new Error("FileManager.write() expects Data");
    __fs("writeBase64", path, data._b64);
  };
})(globalThis);
