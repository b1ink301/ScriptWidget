/* Scriptable-compatible API — Keychain (encrypted with the Android Keystore). */
(function (g) {
  "use strict";

  g.Keychain = {
    contains(key) { return __keychain("contains", String(key)); },
    set(key, value) { __keychain("set", String(key), String(value)); },
    get(key) {
      const value = __keychain("get", String(key));
      if (value == null) throw new Error(`No value for key "${key}" in the Keychain`);
      return value;
    },
    remove(key) { __keychain("remove", String(key)); },
  };
})(globalThis);
