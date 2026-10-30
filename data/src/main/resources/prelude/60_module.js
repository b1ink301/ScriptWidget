/* Scriptable-compatible API — importModule(): other scripts as CommonJS modules. */
(function (g) {
  "use strict";

  const cache = Object.create(null);

  g.importModule = function importModule(name) {
    const key = String(name).replace(/\.js$/, "");
    if (key in cache) return cache[key].exports;
    const source = __module(key);
    if (source == null) throw new Error(`Module "${name}" not found`);
    const fm = FileManager.local();
    const module = { exports: {}, filename: fm.joinPath(fm.documentsDirectory(), `${key}.js`) };
    // Registered before evaluation so circular imports see the partial exports, like Node.
    cache[key] = module;
    new Function("module", "exports", "importModule", source)(module, module.exports, importModule);
    return module.exports;
  };
})(globalThis);
