// Describes the Scriptable API the prelude defines; printed as JSON for ApiCatalog.
const NAMES = __names;
const HIDDEN = new Set(["length", "name", "prototype", "arguments", "caller", "constructor"]);
const source = (fn) => { try { return Function.prototype.toString.call(fn); } catch (e) { return ""; } };
const paramsOf = (src) => {
  const m = src.match(/^(?:async\s+)?(?:function\s*\*?\s*[\w$]*|[\w$]+)?\s*\(([^)]*)\)/) || src.match(/^(?:async\s+)?([\w$]+)\s*=>/);
  if (!m) return [];
  return m[1].split(",").map((p) => p.trim().replace(/\s*=.*$/, "").replace(/^\.\.\./, "")).filter(Boolean);
};
const member = (name, desc, isStatic) => {
  if (desc.get || desc.set) return { name, kind: "Property", params: [], isStatic, isAsync: false };
  const v = desc.value;
  if (typeof v === "function") {
    const src = source(v);
    return { name, kind: "Method", params: paramsOf(src.replace(/^static\s+/, "")), isStatic, isAsync: /^async\b/.test(src) };
  }
  return { name, kind: "Property", params: [], isStatic, isAsync: false };
};
const visible = (n) => !HIDDEN.has(n) && !n.startsWith("_") && !n.startsWith("__");
const out = [];
for (const name of NAMES) {
  const v = globalThis[name];
  if (v === undefined) continue;
  const members = [];
  let kind = "Object", ctor = null;
  if (typeof v === "function") {
    const src = source(v);
    if (/^class\b/.test(src)) {
      kind = "Class";
      const c = src.match(/constructor\s*\(([^)]*)\)/);
      ctor = c ? c[1].split(",").map((p) => p.trim().replace(/\s*=.*$/, "")).filter(Boolean) : [];
      const props = new Set();
      const seen = new Set();
      // Walk the class chain: element classes share a base class (addText, addStack, …).
      for (let cls = v; typeof cls === "function" && cls !== Function.prototype; cls = Object.getPrototypeOf(cls)) {
        for (const m of source(cls).matchAll(/this\.([A-Za-z][\w$]*)\s*=(?!=)/g)) props.add(m[1]);
        if (!cls.prototype) continue;
        for (const n of Object.getOwnPropertyNames(cls.prototype)) {
          if (!visible(n) || seen.has(n)) continue;
          seen.add(n);
          members.push(member(n, Object.getOwnPropertyDescriptor(cls.prototype, n), false));
        }
      }
      for (const n of seen) props.delete(n);
      for (const p of props) members.push({ name: p, kind: "Property", params: [], isStatic: false, isAsync: false });
    } else {
      kind = "Function";
      ctor = null;
      members.length = 0;
      out.push({ name, kind, constructorParams: paramsOf(src), members });
      continue;
    }
  }
  const target = v;
  for (const n of Object.getOwnPropertyNames(target)) {
    if (!visible(n)) continue;
    members.push(member(n, Object.getOwnPropertyDescriptor(target, n), kind === "Class"));
  }
  out.push({ name, kind, constructorParams: ctor, members });
}
console.log(JSON.stringify(out));
