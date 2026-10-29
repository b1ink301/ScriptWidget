// 5. async/await · Promise · setTimeout 동작 확인용
const t0 = Date.now();
const wait = (ms, v) => new Promise((r) => setTimeout(() => r(v), ms));

const results = await Promise.all([wait(300, "A"), wait(100, "B"), sleep(200).then(() => "C")]);
const elapsed = Date.now() - t0;
console.log("results", results, "elapsed", elapsed, "ms");

let cancelled = true;
const id = setTimeout(() => { cancelled = false; }, 50);
clearTimeout(id);
await wait(100);

const w = new ListWidget();
w.backgroundColor = new Color("#312E81");
w.setPadding(14, 14, 14, 14);
w.spacing = 4;

const add = (s, size = 13, color = "#E0E7FF") => {
  const t = w.addText(s);
  t.font = Font.mediumSystemFont(size);
  t.textColor = new Color(color);
};
add("⏱ async / timers", 15, "#FFFFFF");
add(`Promise.all → ${results.join(", ")}`);
add(`elapsed ${elapsed}ms (≈300 expected)`);
add(`clearTimeout ok: ${cancelled}`, 13, cancelled ? "#86EFAC" : "#FCA5A5");

Script.setWidget(w);
