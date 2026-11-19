#!/usr/bin/env python3
"""Generates the screenshot HTML pages from the captured real console output in ../text/.
Chat pages re-colour the captured lines exactly as Renderer/ChatOut do (tone by share, glyphs, buttons)."""
import html, re, os, json

HERE = os.path.dirname(os.path.abspath(__file__))
TEXT = os.path.join(HERE, '..', 'text')
ROOT_PATH = '/home/container'
OLD = r'P:\FoliaRust\DiskUsagePlugin\release\screenshots\server'

PFX = re.compile(r'^\[(\d\d:\d\d:\d\d)\] \[[^\]]*\]: ')


def load(name):
    out = []
    for ln in open(os.path.join(TEXT, name), encoding='utf-8'):
        ln = ln.rstrip('\r\n')
        m = PFX.match(ln)
        if not m:
            continue
        t = ln[m.end():].replace('reports\\', 'reports/').replace(OLD, ROOT_PATH)
        t = t.replace(ROOT_PATH + '\\plugins\\DiskLens/reports/', ROOT_PATH + '/plugins/DiskLens/reports/')
        if ROOT_PATH in t:
            t = t.replace(chr(92), '/')
        out.append((m.group(1), t))
    return out


def esc(s):
    return html.escape(s, quote=False)


# ---------------------------------------------------------------- console panel
CSS_COMMON = """
*{box-sizing:border-box;margin:0}
html,body{width:%dpx}
"""


def console_page(title, blocks, width=1400, note=None, extra=None):
    """blocks: list of (command, [(time, text)...])"""
    rows = []
    n = 0
    for cmd, lines in blocks:
        rows.append('<div class="ln cmd"><span class="pr">&gt;</span> %s</div>' % esc(cmd)); n += 1
        for t, s in lines:
            cls = 'ln'
            if s.startswith('[DiskLens]'):
                cls += ' pl'
            tsx = '[%s INFO]:' % t if t else ''
            rows.append('<div class="%s"><span class="ts">%s</span> %s</div>' % (cls, tsx, esc(s))); n += 1
    body = '\n'.join(rows)
    h = 56 + 46 + n * 26 + 90
    extra_html = extra[0] if extra else ''
    h += extra[1] if extra else 0
    return h, f"""<!doctype html><html><head><meta charset="utf-8"><title>{esc(title)}</title>
<link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600&family=Inter:wght@500;600&display=swap" rel="stylesheet">
<style>{CSS_COMMON % width}
body{{height:{h}px;background:radial-gradient(1200px 600px at 20% -10%,#1d2740,#0c1020 60%);font-family:Inter,sans-serif;padding:36px 40px}}
.win{{background:#0b0e17;border:1px solid #232a3d;border-radius:12px;box-shadow:0 20px 60px rgba(0,0,0,.55);overflow:hidden}}
.bar{{height:46px;background:#141a2b;border-bottom:1px solid #232a3d;display:flex;align-items:center;padding:0 16px;gap:8px;color:#8d98b8;font-size:14px;font-weight:600}}
.dot{{width:12px;height:12px;border-radius:50%}}
.bar .t{{margin-left:12px}}.bar .r{{margin-left:auto;font-weight:500;color:#5f6b8c}}
.term{{padding:14px 20px 16px;font-family:'JetBrains Mono',monospace;font-size:16px;color:#c9d1e6}}
.ln{{height:26px;line-height:26px;white-space:pre;overflow:hidden}}
.ts{{color:#5d6b8f}}.pl{{color:#e6ecff}}
.cmd{{color:#fff;font-weight:600;background:#12192b;margin:6px -20px 4px;padding:0 20px;border-left:3px solid #4f8cff}}
.pr{{color:#4f8cff}}
.note{{margin-top:14px;color:#5f6b8c;font-size:13px;text-align:right}}
</style></head><body>
<div class="win"><div class="bar"><span class="dot" style="background:#ff5f57"></span><span class="dot" style="background:#febc2e"></span><span class="dot" style="background:#28c840"></span><span class="t">{esc(title)}</span><span class="r">Paper 1.21.11 · DiskLens 2.0.0</span></div>
<div class="term">
{body}
</div>{extra_html}</div>
<div class="note">{esc(note or 'Real console output captured from logs/latest.log (demo data)')}</div>
</body></html>"""


# ---------------------------------------------------------------- chat rendering
COL = {'white': '#FFFFFF', 'gray': '#AAAAAA', 'dgray': '#555555', 'aqua': '#55FFFF', 'gold': '#FFAA00',
       'yellow': '#FFFF55', 'green': '#55FF55', 'red': '#FF5555'}
UNITS = {'B': 1, 'KB': 1024, 'MB': 1024 ** 2, 'GB': 1024 ** 3, 'TB': 1024 ** 4,
         'Б': 1, 'КБ': 1024, 'МБ': 1024 ** 2, 'ГБ': 1024 ** 3, 'ТБ': 1024 ** 4}


def tone(share):
    return 'red' if share >= .5 else 'gold' if share >= .2 else 'yellow' if share >= .05 else 'green'


def seg(text, color, bold=False, italic=False, btn=False):
    cls = 'b' if bold else ''
    cls += ' i' if italic else ''
    cls += ' btn' if btn else ''
    return '<span class="%s" style="color:%s">%s</span>' % (cls.strip(), COL[color], esc(text).replace(' ', '&nbsp;'))


def glyphs(prefix):
    return prefix.replace('+- ', '├─ ').replace('\\- ', '└─ ').replace('|  ', '│  ')


ENTRY = re.compile(r'^((?:\|  |   )*(?:\+- |\\- ))(.+?)  (\S+ \S+)(?: \(([\d.]+)%\))?(?:  (\d+ \S+))?$')
MORE = re.compile(r'^((?:\|  |   )*(?:\+- |\\- ))\.\.\. (.*)$')
HEAD = re.compile(r'^== (.+?)  (\S+ \S+), (.*)$')
FILE = re.compile(r'^\s*(\d+)\. (\S+ \S+)  (.+)$')


def chat_lines(lines, ru=False, hover_path=None, total_bytes=20404981947):
    """lines: list of text strings (no timestamps) -> list of (html, hovered?)"""
    up = '[↑ вверх]' if ru else '[↑ up]'
    rescan = '[Пересканировать]' if ru else '[Rescan]'
    report = '[Отчёт в файл]' if ru else '[Report to file]'
    out = []
    for s in lines:
        m = HEAD.match(s)
        if m:
            name, size, counts = m.groups()
            parts = [seg('━━ ', 'dgray'), seg(name, 'aqua', bold=True), seg('  ' + size, 'red'), seg(', ' + counts, 'gray')]
            if not name.startswith('./'):
                parts += [seg('  ', 'gray'), seg(up, 'green', btn=True)]
            out.append(''.join(parts)); continue
        if s.startswith(('Partition:', 'Раздел:')):
            out.append(seg(s, 'dgray')); continue
        if s.startswith(('Rescan:', 'Пересканировать:')):
            continue
        if s.startswith(('Scan:', 'Скан:')):
            out.append(seg(s, 'dgray') + seg('  ', 'gray') + seg(rescan, 'gold', btn=True) + seg(' ', 'gray') + seg(report, 'gold', btn=True)); continue
        if s.startswith(('== Largest', '== Самые')):
            out.append(seg('━━ ' + s[3:], 'gold')); continue
        m = FILE.match(s)
        if m:
            n, size, path = m.groups()
            v, u = size.split()
            share = float(v) * UNITS[u] / total_bytes
            out.append(seg('%3s. ' % n, 'dgray') + seg(size, tone(share)) + seg('  ' + path, 'white')); continue
        m = MORE.match(s)
        if m:
            out.append(seg(glyphs(m.group(1)), 'dgray') + seg('… ' + m.group(2), 'gray', italic=True)); continue
        m = ENTRY.match(s)
        if m:
            pre, name, size, pct, files = m.groups()
            isdir = name.endswith('/')
            share = float(pct) / 100 if pct else 0
            hov = ' hov' if isdir and hover_path and name == hover_path else ''
            h = seg(glyphs(pre), 'dgray') + seg(name, 'aqua' if isdir else 'white')
            h += seg('  ' + size, tone(share))
            if pct: h += seg(' (%s%%)' % pct, 'gray')
            if files: h += seg('  ' + files, 'dgray')
            out.append(h); continue
        out.append(seg(s, 'gray'))
    return out


def chat_page(title, rows, width=1400, tooltip=None, tooltip_row=None, fs=30):
    line_h = 30
    pad = 14
    chat_h = len(rows) * line_h + 2 * pad
    h = max(520, chat_h + 72 + 36 + 260)
    rows_html = '\n'.join('<div class="cl">%s</div>' % r for r in rows)
    tip = ''
    if tooltip:
        top = h - 72 - chat_h + pad + (tooltip_row or 0) * line_h + 40
        tip = '<div class="tip" style="top:%dpx;left:700px"><div>%s</div></div>' % (top, esc(tooltip))
    return h, f"""<!doctype html><html><head><meta charset="utf-8"><title>{esc(title)}</title>
<link href="https://fonts.googleapis.com/css2?family=VT323&family=Pixelify+Sans:wght@400&family=Inter:wght@500&display=swap" rel="stylesheet">
<style>
*{{box-sizing:border-box;margin:0}}
html,body{{width:{width}px;height:{h}px;overflow:hidden}}
body{{position:relative;background:#1c2b3a;font-family:VT323,'Pixelify Sans','DejaVu Sans',sans-serif;font-size:{fs}px;font-variant-ligatures:none}}
.bg{{position:absolute;inset:-60px;filter:blur(14px);background:
 radial-gradient(circle at 78% 18%,#ffe9a8 0 6%,transparent 7%),
 linear-gradient(180deg,#8fc7ee 0%,#bfe0f3 42%,#6a9f4f 42.2%,#5a8c42 62%,#6b5a43 62.2%,#4e4030 100%)}}
.bg:after{{content:"";position:absolute;inset:0;background:
 repeating-linear-gradient(90deg,rgba(0,0,0,.06) 0 80px,transparent 80px 160px),
 repeating-linear-gradient(0deg,rgba(0,0,0,.05) 0 80px,transparent 80px 160px)}}
.chat{{position:absolute;left:12px;bottom:72px;width:{width-24}px;background:rgba(0,0,0,.5);padding:{pad}px 12px}}
.cl{{height:{line_h}px;line-height:{line_h}px;white-space:pre;color:#fff;text-shadow:2px 2px 0 rgba(0,0,0,.45);overflow:hidden}}
.cl span{{font-family:VT323,'Pixelify Sans','DejaVu Sans Mono',monospace}}
.b{{font-weight:400;text-shadow:2px 2px 0 rgba(0,0,0,.45),1px 0 0 currentColor}}.i{{font-style:italic}}
.btn{{text-decoration:none}}
.hov{{text-decoration:underline}}
.input{{position:absolute;left:12px;bottom:24px;width:{width-24}px;height:36px;background:rgba(0,0,0,.5);border:0;color:#e0e0e0;line-height:36px;padding-left:10px;font-size:30px}}
.tip{{position:absolute;background:rgba(16,0,16,.94);border:3px solid #2a0a5e;border-top-color:#5000aa;border-bottom-color:#28007f;padding:6px 12px;color:#fff;font-size:30px;z-index:5;box-shadow:0 0 0 3px rgba(16,0,16,.94)}}
.mark{{position:absolute;right:16px;top:12px;font-family:Inter,sans-serif;font-size:14px;color:rgba(255,255,255,.85);background:rgba(0,0,0,.45);padding:6px 12px;border-radius:6px}}
</style></head><body>
<div class="bg"></div>
<div class="mark">Rendered from real plugin output</div>
<div class="chat" style="height:{chat_h}px">
{rows_html}
</div>
<div class="input">&nbsp;</div>
{tip}
</body></html>"""


def top_only(ls):
    # same scan, depth 1: drop nested rows (equivalent to `disklens ./ 1`)
    return [x for x in ls if not x.startswith(('|  ', '   '))]


# ---------------------------------------------------------------- build
def block(lines, start_pred, nstop=1):
    """Return text lines from the first line matching start_pred up to and including the 'Rescan' line."""
    i = next(k for k, (_, s) in enumerate(lines) if start_pred(s))
    res = []
    for t, s in lines[i:]:
        res.append((t, s))
        if s.startswith(('Rescan:', 'Пересканировать:')):
            break
    return res


def main():
    en = load('console-en.log')
    ru = load('console-ru.log')
    pages = {}

    root_en = block(en, lambda s: s.startswith('== ./'))
    plug = block(en, lambda s: s.startswith('== plugins/'))
    files_en = block(en, lambda s: s.startswith('== Largest'))
    blue = block(en, lambda s: s.startswith('== bluemap/'))
    back = block(en, lambda s: s.startswith('== backups/'))
    status = [(t, s) for t, s in en if s.startswith('No scan is running')]
    rep = [(t, s) for t, s in en if 'Report written' in s]
    root_ru = block(ru, lambda s: s.startswith('== ./'))
    files_ru = block(ru, lambda s: s.startswith('== Самые'))
    reload_ru = [(t, s) for t, s in ru if s.startswith('Конфиг и языки')]

    T = lambda b: [s for _, s in b]

    # 01 chat tree
    h, p = chat_page('DiskLens tree', chat_lines(top_only(T(root_en))))
    pages['01-tree-chat'] = (h, p)
    # 02 console panel
    h, p = console_page('Console — disklens', [('disklens', root_en[:28] + [('', '⋮  (output continues, cropped for the screenshot)')])])
    pages['02-console-panel'] = (h, p)
    # 03 largest files (chat)
    h, p = chat_page('DiskLens files', chat_lines(T(files_en)))
    pages['03-largest-files'] = (h, p)
    # 04 report: console + report file excerpt
    rep_lines = open(os.path.join(TEXT, 'report-sample.txt'), encoding='utf-8').read().replace(OLD, ROOT_PATH).splitlines()
    rep_lines = [x for x in rep_lines[:6]] + [''] + [x for x in rep_lines[7:] if len(x) - len(x.lstrip('│ ├└─')) <= 6 and not x.startswith('│  │')][:14] + ['…']
    pre = '\n'.join('<div class="ln">%s</div>' % esc(x) for x in rep_lines)
    extra = ('<div class="bar" style="border-top:1px solid #232a3d"><span class="t">plugins/DiskLens/reports/report-20261119-234000.txt</span><span class="r">File manager · text view</span></div>'
             '<div class="term" style="color:#dbe3f7;background:#0e1322">%s</div>' % pre, 46 + 32 + len(rep_lines) * 26)
    h, p = console_page('Console — disklens export', [('disklens export', rep + status and rep)], extra=extra,
                        note='Real console output and report file (demo data)')
    pages['04-report'] = (h, p)
    # 05 russian chat
    h, p = chat_page('DiskLens ru', chat_lines(top_only(T(root_ru)), ru=True), fs=24)
    pages['05-russian'] = (h, p)
    # 06 drill-down chat with hover
    rows = chat_lines(T(blue), hover_path='tiles/')
    idx = next(i for i, r in enumerate(rows) if 'tiles/' in r)
    h, p = chat_page('DiskLens drill-down', rows, tooltip='Open bluemap/web/maps/world/tiles/', tooltip_row=idx)
    pages['06-drill-down-chat'] = (h, p)
    # 07 console: plugins, status, backups
    h, p = console_page('Console — plugins, status, backups',
                        [('disklens plugins/ 1 5', plug), ('disklens status', status), ('disklens backups/', back)])
    pages['07-console-plugins'] = (h, p)
    # 08 russian console? (kept as chat only)
    meta = {}
    for name, (h, page) in pages.items():
        open(os.path.join(HERE, name + '.html'), 'w', encoding='utf-8').write(page)
        meta[name] = h
    json.dump(meta, open(os.path.join(HERE, 'sizes.json'), 'w'))
    print(meta)


main()
