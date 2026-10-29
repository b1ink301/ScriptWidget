// 3. 이미지 위젯: Request.loadImage() + 가로 스택
const [img, info] = await Promise.all([
  new Request("https://picsum.photos/seed/" + Math.floor(Date.now() / 900000) + "/400/400").loadImage(),
  new Request("https://api.github.com/repos/dokar3/quickjs-kt").loadJSON(),
]);

const w = new ListWidget();
w.backgroundColor = new Color("#111827");
w.setPadding(12, 12, 12, 12);
w.url = "https://github.com/dokar3/quickjs-kt";

const row = w.addStack();
row.spacing = 10;

const pic = row.addImage(img);
pic.imageSize = new Size(64, 64);
pic.cornerRadius = 12;

const col = row.addStack();
col.layoutVertically();
col.spacing = 2;

const name = col.addText(info.full_name);
name.font = Font.boldSystemFont(15);
name.textColor = Color.white();
name.lineLimit = 1;

const stars = col.addText(`★ ${info.stargazers_count}  ⑂ ${info.forks_count}`);
stars.font = Font.systemFont(12);
stars.textColor = new Color("#FBBF24");

w.addSpacer();
const desc = w.addText(info.description ?? "");
desc.font = Font.systemFont(11);
desc.textColor = new Color("#9CA3AF");
desc.lineLimit = 2;

Script.setWidget(w);
