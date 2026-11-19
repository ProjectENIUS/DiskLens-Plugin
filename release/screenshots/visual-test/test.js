// DiskLens in-game visual autotest. See run.md.
// Starts a throwaway Paper server on port 25800, joins with Mineflayer bots, checks the chat components,
// renders them to PNG and compares with baseline.json.
const fs = require('fs'), path = require('path'), cp = require('child_process');
const mineflayer = require('mineflayer');

const ROOT = path.resolve(__dirname, '..', '..', '..');           // DiskUsagePlugin
const SRV = path.join(__dirname, 'server');
const OUT = path.join(__dirname, 'out');
const PORT = 25800;
const JAVA = process.env.JAVA || 'C:\\Program Files\\Zulu\\zulu-21\\bin\\java.exe';
const TMP = path.join(ROOT, '.tmp');
const PAPER = process.env.PAPER_JAR || path.join(ROOT, 'itest', 'cache', 'paper-1.21.11.jar'); // read-only copy source
const PLUGIN = process.env.PLUGIN_JAR || path.join(ROOT, 'build', 'libs', 'DiskLens-2.0.0.jar');
const VERSION = process.env.MC_VERSION || '1.21.11';
const UPDATE = process.argv.includes('--update-baseline');
const KEEP = process.argv.includes('--keep-server');

const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [];
function check(name, ok, detail) { results.push({ name, ok: !!ok, detail }); console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : '  -> ' + (detail || ''))); }
function warn(name, detail) { console.log('WARN ' + name + ' -> ' + detail); }

// ---------------------------------------------------------------- demo data
function sparse(rel, mb) {
  const f = path.join(SRV, rel); fs.mkdirSync(path.dirname(f), { recursive: true });
  fs.writeFileSync(f, '');
  cp.spawnSync('fsutil', ['sparse', 'setflag', f]);
  const fd = fs.openSync(f, 'r+'); fs.ftruncateSync(fd, Math.round(mb * 1048576)); fs.closeSync(fd);
}
function seed() {
  fs.rmSync(SRV, { recursive: true, force: true });
  fs.mkdirSync(path.join(SRV, 'plugins'), { recursive: true });
  fs.copyFileSync(PAPER, path.join(SRV, 'paper.jar'));
  fs.copyFileSync(PLUGIN, path.join(SRV, 'plugins', path.basename(PLUGIN)));
  fs.writeFileSync(path.join(SRV, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(SRV, 'server.properties'),
    `server-port=${PORT}\nonline-mode=false\nenable-rcon=false\nlevel-type=minecraft\\:flat\nview-distance=2\nsimulation-distance=2\nlevel-name=lobby\nmotd=Demo\n`);
  sparse('backups/world-2024-05-01.zip', 3200); sparse('backups/world-2024-05-15.zip', 3350);
  sparse('backups/world-2024-06-01.zip', 3500); sparse('backups/plugins-2024-06-01.zip', 180);
  for (let i = 1; i <= 40; i++) sparse(`bluemap/web/maps/world/tiles/0/x${i}/z1.png`, 38);
  for (let i = 1; i <= 25; i++) sparse(`bluemap/web/maps/world/tiles/1/x${i}/z1.png`, 21);
  sparse('bluemap/web/maps/world/hires/lod.json', 900);
  sparse('plugins/CoreProtect/database.db', 1450); sparse('plugins/Dynmap/web/tiles/world/flat/0_0.png', 700);
  sparse('plugins/WorldEdit/schematics/spawn.schem', 48); sparse('plugins/Essentials/userdata/players.dat', 24);
  for (let i = 1; i <= 14; i++) sparse(`logs/2024-06-${String(i).padStart(2, '0')}-1.log.gz`, 45);
  sparse('logs/debug.log', 310); sparse('world_nether/region/r.0.0.mca', 220);
  for (let i = 0; i < 4; i++) sparse(`world/region/r.${i}.0.mca`, 480);
  for (let i = 0; i < 4000; i++) { // many small files so the scan has something to count
    const f = path.join(SRV, 'cache-demo', 'd' + (i % 200), 'f' + i + '.bin'); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, 'x');
  }
}

// ---------------------------------------------------------------- server
let server, serverLog = '';
function startServer() {
  fs.mkdirSync(TMP, { recursive: true });
  server = cp.spawn(JAVA, ['-Xmx1G', '-Dfile.encoding=UTF-8', '-Djava.io.tmpdir=' + TMP, '-Djdk.net.unixdomain.tmpdir=' + TMP,
    '-jar', 'paper.jar', 'nogui'], { cwd: SRV });
  server.stdout.on('data', d => serverLog += d.toString('utf8'));
  server.stderr.on('data', d => serverLog += d.toString('utf8'));
  return new Promise((res, rej) => {
    const t = setInterval(() => { if (/Done \(/.test(serverLog)) { clearInterval(t); res(); } }, 500);
    setTimeout(() => { clearInterval(t); rej(new Error('server start timeout')); }, 240000);
  });
}
const cmd = c => server.stdin.write(c + '\n');
async function stopServer() {
  if (!server) return;
  cmd('stop');
  await new Promise(r => { server.on('exit', r); setTimeout(r, 30000); });
}

// ---------------------------------------------------------------- components
const COLORS = { black: '#000000', dark_blue: '#0000AA', dark_green: '#00AA00', dark_aqua: '#00AAAA', dark_red: '#AA0000', dark_purple: '#AA00AA',
  gold: '#FFAA00', gray: '#AAAAAA', dark_gray: '#555555', blue: '#5555FF', green: '#55FF55', aqua: '#55FFFF', red: '#FF5555',
  light_purple: '#FF55FF', yellow: '#FFFF55', white: '#FFFFFF' };
function hoverText(h) {
  if (!h) return null;
  const v = h.value !== undefined ? h.value : (h.contents !== undefined ? h.contents : h.text);
  return textOf(v);
}
function textOf(n) {
  if (n == null) return '';
  if (typeof n === 'string') return n;
  if (Array.isArray(n)) return n.map(textOf).join('');
  return (n.text || '') + textOf(n.extra) + (n.translate ? n.translate : '');
}
/** Flatten a JSON chat component into [{text,color,bold,italic,click,hover}] with inheritance. */
function flatten(node, inh = {}, out = []) {
  if (node == null) return out;
  if (typeof node === 'string') { out.push({ ...inh, text: node }); return out; }
  if (Array.isArray(node)) { node.forEach(n => flatten(n, inh, out)); return out; }
  const st = { ...inh };
  if (node.color) st.color = node.color;
  if (node.bold !== undefined) st.bold = !!node.bold;
  if (node.italic !== undefined) st.italic = !!node.italic;
  const ce = node.click_event || node.clickEvent;
  if (ce) st.click = (ce.action || '') + ':' + (ce.command || ce.value || ce.url || '');
  const he = node.hover_event || node.hoverEvent;
  if (he) st.hover = hoverText(he);
  if (node.text !== undefined && node.text !== '') out.push({ ...st, text: node.text });
  else if (node.translate) out.push({ ...st, text: node.translate });
  (node.extra || []).forEach(n => flatten(n, st, out));
  return out;
}
function norm(segs) { // merge neighbours, strip volatile attributes
  return segs.map(s => ({ t: s.text, c: s.color || 'white', b: !!s.bold, i: !!s.italic, k: s.click || null, h: s.hover || null }));
}
const lineText = l => l.map(s => s.t).join('');
const volatile = t => t.replace(/\d+(\.\d+)? ?(s|с|min|мин)\b/g, 'N s').replace(/\d+ s ago|\d+ с назад/g, 'N ago');

// ---------------------------------------------------------------- bot
function makeBot(name, locale) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, version: VERSION, auth: 'offline', locale, viewDistance: 'tiny' });
  bot.captured = []; bot.actionbar = [];
  bot.on('message', (msg, pos) => {
    const segs = norm(flatten(msg.json));
    if (pos === 'game_info') bot.actionbar.push(lineText(segs)); else bot.captured.push(segs);
  });
  bot.on('error', e => console.log('bot error', e.message));
  bot.on('kicked', r => console.log('kicked', JSON.stringify(r)));
  return bot;
}
async function run(bot, command, { waitFor, quiet = 2500, timeout = 30000 } = {}) {
  bot.captured.length = 0;
  bot.chat(command);
  const t0 = Date.now(); let last = Date.now(), n = 0;
  while (Date.now() - t0 < timeout) {
    await sleep(200);
    if (bot.captured.length !== n) { n = bot.captured.length; last = Date.now(); }
    const txt = bot.captured.map(lineText).join('\n');
    if (waitFor ? (waitFor.test(txt) && Date.now() - last > 800) : (n > 0 && Date.now() - last > quiet)) break;
  }
  return bot.captured.slice();
}

// ---------------------------------------------------------------- rendering
const CSS = `*{box-sizing:border-box;margin:0}html,body{width:1400px;overflow:hidden}
body{position:relative;background:#1c2b3a;font-family:VT323,'Pixelify Sans','DejaVu Sans',sans-serif;font-size:30px;font-variant-ligatures:none}
.bg{position:absolute;inset:-60px;filter:blur(14px);background:radial-gradient(circle at 78% 18%,#ffe9a8 0 6%,transparent 7%),linear-gradient(180deg,#8fc7ee 0%,#bfe0f3 42%,#6a9f4f 42.2%,#5a8c42 62%,#6b5a43 62.2%,#4e4030 100%)}
.chat{position:absolute;left:12px;bottom:72px;width:1376px;background:rgba(0,0,0,.5);padding:14px 12px}
.cl{height:30px;line-height:30px;white-space:pre;color:#fff;text-shadow:2px 2px 0 rgba(0,0,0,.45);overflow:hidden}
.cl span{font-family:VT323,'Pixelify Sans','DejaVu Sans Mono',monospace}.b{text-shadow:2px 2px 0 rgba(0,0,0,.45),1px 0 0 currentColor}.i{font-style:italic}
.ab{position:absolute;left:0;right:0;bottom:116px;text-align:center;color:#fff;text-shadow:2px 2px 0 rgba(0,0,0,.45)}
.input{position:absolute;left:12px;bottom:24px;width:1376px;height:36px;background:rgba(0,0,0,.5)}
.mark{position:absolute;right:16px;top:12px;font:14px sans-serif;color:#fff;background:rgba(0,0,0,.45);padding:6px 12px;border-radius:6px}`;
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/ /g, '&nbsp;');
function pageFor(lines, title) {
  const rows = lines.map(l => '<div class="cl">' + l.map(s => `<span class="${s.b ? 'b' : ''} ${s.i ? 'i' : ''}" style="color:${COLORS[s.c] || s.c}">${esc(s.t)}</span>`).join('') + '</div>').join('');
  const ch = lines.length * 30 + 28, h = Math.max(520, ch + 72 + 36 + 200);
  return [h, `<!doctype html><meta charset=utf-8><title>${title}</title><link href="https://fonts.googleapis.com/css2?family=VT323&family=Pixelify+Sans&display=swap" rel=stylesheet><style>${CSS}body{height:${h}px}html{height:${h}px}</style><div class=bg></div><div class=mark>Rendered from captured chat components</div><div class=chat style="height:${ch}px">${rows}</div><div class=input></div>`];
}
function shot(name, lines) {
  const [h, html] = pageFor(lines, name);
  const f = path.join(OUT, name + '.html'); fs.writeFileSync(f, html);
  const edge = ['C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'].find(fs.existsSync);
  if (!edge) return warn('render ' + name, 'no Edge/Chrome found');
  cp.spawnSync(edge, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1', `--window-size=1400,${h}`,
    '--virtual-time-budget=8000', '--screenshot=' + path.join(OUT, name + '.png'), 'file:///' + f.replace(/\\/g, '/')], { stdio: 'ignore' });
}

// ---------------------------------------------------------------- main
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const baseline = {}; const raw = {};
  try {
    seed();
    await startServer();
    cmd('op DiskTester'); cmd('op DiskTesterRu');
    await sleep(1500);

    // ---------- English client
    const bot = makeBot('DiskTester', 'en_US');
    await new Promise(r => bot.once('spawn', r)); await sleep(2000);

    const tree = await run(bot, '/disklens', { waitFor: /Scan: .*(s ago|took)/ });
    raw.tree = tree;
    const T = tree.map(lineText);
    check('tree: header line', T.some(l => /^━━ \.\/ \(server root\)\s+[\d.]+ GB, [\d\s ]+ files, [\d\s ]+ folders/.test(l)), T.slice(0,3).join('|'));
    check('tree: unicode glyphs (├─ └─)', T.some(l => l.startsWith('├─ ')) && T.some(l => l.startsWith('└─ ')));
    check('tree: backups/ listed', T.some(l => /backups\/\s+10\.0 GB \(\d+\.\d%\)/.test(l)));
    const entry = tree.find(l => lineText(l).includes('backups/'));
    const sg = n => entry && entry.find(s => s.t.includes(n));
    check('colour: folder name aqua', sg('backups/') && sg('backups/').c === 'aqua', JSON.stringify(entry));
    check('colour: size >=50% red', entry && entry.some(s => /10\.0 GB/.test(s.t) && s.c === 'red'));
    const bm = tree.find(l => lineText(l).includes('bluemap/'));
    check('colour: size 5-20% yellow', bm && bm.some(s => /\d GB/.test(s.t) && s.c === 'yellow'), JSON.stringify(bm));
    const lg = tree.find(l => lineText(l).startsWith('├─ world_nether/'));
    check('colour: size <5% green', lg && lg.some(s => /220 MB/.test(s.t) && s.c === 'green'));
    check('colour: branch dark_gray, percent gray', entry && entry[0].c === 'dark_gray' && entry.some(s => /\(\d+\.\d%\)/.test(s.t) && s.c === 'gray'));
    check('click: folder run_command', entry && entry.some(s => s.t.includes('backups/') && s.k === 'run_command:/disklens backups/ 2 10' || (s.k || '').startsWith('run_command:/disklens backups/')), JSON.stringify(entry));
    check('hover: folder tooltip', entry && entry.some(s => (s.h || '').includes('backups/')), JSON.stringify(entry));
    const foot = tree.find(l => lineText(l).startsWith('Scan:'));
    check('buttons: [Rescan] gold + click', foot && foot.some(s => s.t === '[Rescan]' && s.c === 'gold' && /run_command:\/disklens .*-r/.test(s.k || '')), JSON.stringify(foot));
    check('buttons: [Report to file] gold + click', foot && foot.some(s => s.t === '[Report to file]' && s.c === 'gold' && /run_command:\/disklens export/.test(s.k || '')));

    const plug = await run(bot, '/disklens plugins/ 1 5', { waitFor: /Scan: / });
    raw.plugins = plug; const P = plug.map(lineText);
    check('plugins: header', P.some(l => l.startsWith('━━ plugins/')));
    const up = plug.find(l => l.some(s => s.t === '[↑ up]'));
    check('plugins: [↑ up] green + click to parent', up && up.some(s => s.t === '[↑ up]' && s.c === 'green' && /^run_command:\/disklens \.\/ /.test(s.k || '')), JSON.stringify(up));
    check('plugins: top 5 + "more" line', P.filter(l => /^[├└]─ /.test(l)).length === 6 && P.some(l => /… \d+ more/.test(l)), P.join('|'));
    check('plugins: CoreProtect first', /CoreProtect\//.test(P.find(l => l.startsWith('├─ '))));

    const files = await run(bot, '/disklens files 5', { waitFor: /Scan: / });
    raw.files = files; const F = files.map(lineText);
    check('files: title gold', files[0].some(s => /Largest files on the server \(5 of/.test(s.t) && s.c === 'gold'), JSON.stringify(files[0]));
    check('files: 5 rows, biggest first', F.filter(l => /^\s*\d+\. /.test(l)).length === 5 && /1\. 3\.4 GB\s+backups\/world-2024-06-01\.zip/.test(F[1]), F.join('|'));
    check('files: paths white', files[1].some(s => /backups\//.test(s.t) && s.c === 'white'));

    const status = await run(bot, '/disklens status', {});
    raw.status = status;
    check('status: cached result', status.map(lineText).some(l => /Cached result from .*GB, [\d\s ]+ files/.test(l)), JSON.stringify(status.map(lineText)));

    bot.actionbar.length = 0;
    const rescan = await run(bot, '/disklens ./ -r', { waitFor: /Scan: .*took/, timeout: 60000 });
    raw.rescan = rescan;
    check('rescan: result delivered', rescan.map(lineText).some(l => l.startsWith('Scan:')));
    if (bot.actionbar.length) check('rescan: action-bar progress', bot.actionbar.some(a => /Scanning: [\d,.\s]+files/.test(a)), bot.actionbar.join('|'));
    else warn('rescan: action-bar progress', 'scan finished before any progress tick (demo tree is small); not a failure');

    // ---------- Russian client
    const ru = makeBot('DiskTesterRu', 'ru_RU');
    await new Promise(r => ru.once('spawn', r)); ru.setSettings({ locale: 'ru_RU' }); await sleep(3000);
    const rt = await run(ru, '/disklens', { waitFor: /Скан: / });
    raw.russian = rt; const R = rt.map(lineText);
    check('ru: header "корень сервера"', R.some(l => /корень сервера/.test(l)), R.join('|'));
    check('ru: units ГБ/МБ', R.some(l => /\d ГБ/.test(l)) && R.some(l => /\d МБ/.test(l)));
    const rf = rt.find(l => lineText(l).startsWith('Скан:'));
    check('ru: buttons [Пересканировать]/[Отчёт в файл]', rf && rf.some(s => s.t === '[Пересканировать]') && rf.some(s => s.t === '[Отчёт в файл]'));
    ru.quit();
    bot.quit();

    // ---------- baseline + render
    const volatileLines = ls => ls.map(l => l.map(s => ({ ...s, t: volatile(s.t) })));
    for (const k of ['tree', 'plugins', 'files', 'status', 'rescan', 'russian']) baseline[k] = volatileLines(raw[k]);
    for (const [k, v] of Object.entries({ '01-tree': raw.tree, '02-plugins': raw.plugins, '03-files': raw.files, '04-status': raw.status, '05-russian': raw.russian }))
      shot(k, v);
    fs.writeFileSync(path.join(OUT, 'captured-components.json'), JSON.stringify(raw, null, 1));
    const bp = path.join(__dirname, 'baseline.json');
    if (UPDATE || !fs.existsSync(bp)) { fs.writeFileSync(bp, JSON.stringify(baseline, null, 1)); console.log('baseline written: ' + bp); }
    else {
      const old = JSON.parse(fs.readFileSync(bp, 'utf8'));
      for (const k of Object.keys(baseline)) {
        const a = JSON.stringify(old[k]), b = JSON.stringify(baseline[k]);
        check('baseline: ' + k, a === b, 'differs from baseline.json (captured: out/captured-components.json); rerun with --update-baseline if intended');
      }
    }
  } catch (e) {
    check('harness', false, e.stack || e.message);
  } finally {
    await stopServer();
    if (!KEEP) { await sleep(1500); fs.rmSync(SRV, { recursive: true, force: true }); }
  }
  const failed = results.filter(r => !r.ok).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  fs.writeFileSync(path.join(OUT, 'results.json'), JSON.stringify(results, null, 1));
  process.exit(failed ? 1 : 0);
})();
