# DiskLens

> Format note: CurseForge's editor defaults to WYSIWYG, with Markdown as an alternative editor mode (verified on the CurseForge submission guide). Section 1 is Markdown: switch the editor to Markdown and paste it. Section 2 is an HTML variant for the WYSIWYG source view. Do not paste both.

---

## 1. Markdown version (paste this)

**DiskLens shows which folders on your Minecraft server take up disk space.** A tree sorted by size, with sizes, the percent of the parent folder, and a top-N per level. Works in chat, in the server console, in your hosting panel and over RCON.

One jar for **Spigot, Paper, Purpur and Folia**, Minecraft **1.16.5+**.

## Why DiskLens?

Many hosting panels don't show how big each folder is. Your server is "full", and you have no idea whether it is the worlds, the backups, the logs or a map plugin. DiskLens answers that in one command.

On a real Folia hosting server it found **152 GB of web-map tiles** for the Nether/End maps and a **22 GB CoreProtect database**. Neither was visible in the panel.

## Features

- Folder tree sorted by size: size, percent of the parent, top-N per level. The rest collapses into a "N more" line.
- Clickable folders in chat: click to drill down, with `[↑ up]`, `[Rescan]` and `[Report to file]` buttons.
- Plain-text output with an ASCII tree in the console, hosting panel and RCON, readable everywhere.
- `files` lists the largest files on the server.
- `export` writes a full text report to `plugins/DiskLens/reports/`.
- Results are cached (5 minutes by default). Add `-r` to rescan.
- Scanning is async only, so it doesn't block the main thread or Folia region threads.
- Symlinks are not followed. `..` and absolute paths cannot leave the server folder.
- Tab completion for folder names.
- Languages: English and Russian. Players get their client language. Add your own file to add a language.
- `config.yml` and language files update themselves: missing keys are added, your edits are kept (a `config.yml.bak` backup is saved).
- Safe to unload/load with PlugMan / PlugManX.

## Commands and permissions

Command: `/disklens`, alias `/du` (configurable). In the console you can omit the slash.

| Command | What it does |
|---|---|
| `/disklens` | Tree from the server root (default depth 2, top 10) |
| `/disklens <path> [depth] [topN]` | Tree of a subfolder, e.g. `/disklens world 3 15` |
| `/disklens files [N]` | Largest files on the server |
| `/disklens export [path]` | Full report to `plugins/DiskLens/reports/` |
| `/disklens status` | Scan state and cache |
| `/disklens reload` | Reload `config.yml` and languages |
| `/disklens help` | Help |
| add `-r` | Rescan, ignore the cache |

Folders with numeric names need a trailing slash: `/disklens 2024/`.

Permission: `disklens.use` (default: op). The console and RCON always have it.

## Example output

Generic example (console / panel):

```
Server root: ./ (server root)
./ (server root)  86.4 GB
+- world/            41.2 GB  47.7%
|  \- region/        40.8 GB  99.0%
+- backups/          22.5 GB  26.0%
+- plugins/          12.9 GB  14.9%
|  +- SomePlugin/    11.6 GB  89.9%
|  \- 14 more (1.3 GB)
+- logs/              5.1 GB   5.9%
\- 6 more (4.7 GB)
Partition: 86.4 GB used of 200 GB, 113.6 GB free (your hosting quota may differ)
```

In chat the same tree is colored (red at 50% or more, orange at 20% or more, yellow at 5% or more) and the folders are clickable.

## Configuration

```yaml
language: auto             # "auto" = player's client language, or en / ru
default-language: en       # console, RCON, fallback
command-aliases: [du]      # [] to disable
cache-minutes: 5
default-depth: 2
default-top: 10
files-per-folder: 50
largest-files: 100
max-chat-lines: 250
max-console-lines: 1000
console-ascii-tree: true
report-ascii-tree: false
progress-log-seconds: 5
```

## PlugMan / PlugManX

DiskLens unloads cleanly: it cancels its tasks, stops a running scan, clears the cache and removes its tab completer. Loading it again recreates everything without duplicates. The alias is re-registered on load.

## Supported servers and versions

- Platforms: Spigot, Paper, Purpur, Folia (`folia-supported: true`).
- Minecraft 1.16.5 and newer. Java 8+ for the core, Java 17+ for the Folia part.
- Tested on real servers (all pass): Paper 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.4, 1.21.11; Purpur 1.16.5, 1.20.4, 1.21.11; Spigot 1.16.5, 1.21.11; Folia 1.19.4, 1.20.6, 1.21.11.
- Folia 1.19.4: everything works except RCON, because Folia 1.19.4 itself rejects all RCON commands.
- PlugMan / PlugManX was verified on 1.20.6 and newer.
- Minecraft 26.x is not tested yet.

## FAQ

**Does it lag the server?** No. Scanning is async only and the result is cached.

**Does it follow symlinks?** No. Links are skipped and counted in the footer.

**The sizes differ from my hosting quota.** DiskLens sums the file sizes. The "Partition" line shows the whole disk, and your quota may be smaller.

**Can I use it over RCON?** Yes. With a cached result the answer is immediate. Without one, the scan starts in the background and the result goes to the log; repeat the command in a few seconds.

**Does it change or delete anything?** No. It only reads, and it writes only the reports you request.

**Migrating from DiskUsage?** DiskLens is the new name of DiskUsage (2.0.0). The command is `/disklens`, the permission is `disklens.use`.

**More languages?** Copy `plugins/DiskLens/lang/en.yml` to `<code>.yml` and translate it.

---

## 2. HTML version (for the WYSIWYG source view)

```html
<p><strong>DiskLens shows which folders on your Minecraft server take up disk space.</strong> A tree sorted by size, with sizes, percent of the parent and top-N per level. Works in chat, the server console, your hosting panel and RCON.</p>
<p>One jar for <strong>Spigot, Paper, Purpur and Folia</strong>, Minecraft <strong>1.16.5+</strong>.</p>

<h2>Why DiskLens?</h2>
<p>Many hosting panels don't show folder sizes. Your server is "full" and you can't tell if it is the worlds, backups, logs or a map plugin. DiskLens answers in one command. On a real Folia server it found <strong>152 GB of web-map tiles</strong> and a <strong>22 GB CoreProtect database</strong> that the panel didn't show.</p>

<h2>Features</h2>
<ul>
<li>Folder tree sorted by size, with percent of parent and top-N per level ("N more" for the rest)</li>
<li>Clickable folders in chat</li>
<li>Plain-text ASCII tree in console, panel and RCON</li>
<li><code>files</code>: largest files; <code>export</code>: full text report</li>
<li>Cached results, <code>-r</code> to rescan</li>
<li>Async scanning only; symlinks not followed; cannot leave the server folder</li>
<li>English and Russian, players get their client language</li>
<li>Auto-updating config and language files</li>
<li>PlugMan / PlugManX safe</li>
</ul>

<h2>Commands and permissions</h2>
<p>Command <code>/disklens</code>, alias <code>/du</code>. Permission <code>disklens.use</code> (op by default; console and RCON always allowed).</p>
<ul>
<li><code>/disklens [path] [depth] [topN] [-r]</code>: tree</li>
<li><code>/disklens files [N]</code>: largest files</li>
<li><code>/disklens export [path]</code>: report to <code>plugins/DiskLens/reports/</code></li>
<li><code>/disklens status</code>, <code>reload</code>, <code>help</code></li>
</ul>

<h2>Example output</h2>
<pre>./ (server root)  86.4 GB
+- world/            41.2 GB  47.7%
|  \- region/        40.8 GB  99.0%
+- backups/          22.5 GB  26.0%
+- plugins/          12.9 GB  14.9%
|  +- SomePlugin/    11.6 GB  89.9%
|  \- 14 more (1.3 GB)
+- logs/              5.1 GB   5.9%
\- 6 more (4.7 GB)</pre>

<h2>Supported servers</h2>
<p>Spigot, Paper, Purpur, Folia. Minecraft 1.16.5 to 1.21.11 tested (Paper, Purpur, Spigot; Folia 1.19.4 and 1.20.6+; no RCON on Folia 1.19.4, a Folia limitation). Minecraft 26.x not tested.</p>

<h2>FAQ</h2>
<p><strong>Does it lag the server?</strong> No, scanning is async and cached.<br>
<strong>Does it change files?</strong> No, it only reads (and writes reports you request).<br>
<strong>RCON?</strong> Works: cached results return at once, otherwise the scan runs in the background and the result goes to the log.</p>
```
