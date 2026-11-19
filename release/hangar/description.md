# DiskLens

**See exactly which folders eat your server's disk space, right from chat, the console, your hosting panel or RCON.**

DiskLens (formerly *DiskUsage*) scans your server folder asynchronously and shows a tree sorted by size, with the size of each entry and its percent of the parent folder. One jar works on Spigot, Paper, Purpur and Folia.

> Most hosting panels tell you "your disk is 97% full" and nothing else. DiskLens tells you *why*.

---

## Why DiskLens?

Many hosting panels show only the total disk usage, not the size of individual folders. When the disk fills up you are left guessing: world files? old backups? logs? a web map? a database?

DiskLens answers that in one command:

- finds the biggest folders and files without leaving the game or the panel console;
- works with **no player online** (console, panel console, RCON);
- does not block the server: scanning runs only on async threads;
- is safe: it never follows symlinks and can never leave the server folder.

Typical culprits it finds: world `region` folders, forgotten backups, huge `logs/` and `crash-reports/`, web-map tiles (BlueMap/Dynmap), logging databases (CoreProtect and similar), `*.hprof` dumps. In one real case on a Folia host it exposed over 150 GB of web-map tiles and a 22 GB logging database that the panel never showed.

## Features

- **Size-sorted folder tree**: size and percent of the parent folder on every line.
- **Top-N per level**: the rest collapses into a single `N more (size)` line, so output stays readable.
- **Clickable in chat**: click a folder to open it, plus `[up]`, `[Rescan]` and `[Report to file]` buttons.
- **Plain text for console, hosting panel and RCON**: ASCII tree (`+-`, `\-`, `|`) that renders in any panel.
- **`files`**: the largest files on the whole server.
- **`export`**: a full text report without limits, saved to `plugins/DiskLens/reports/`.
- **Cache + `-r`**: results are cached (5 min by default); add `-r` to rescan.
- **Async-only scanning**, progress in the action bar (players) or the server log (console).
- **Safe by design**: symlinks and junctions are not followed, `..` and absolute paths outside the server root are rejected, folders without access are skipped and counted.
- **Languages**: English and Russian built in. Players get their own client language automatically; add your own translation by dropping a file into `plugins/DiskLens/lang/`.
- **Auto-updating `config.yml` and language files**: new options are added on update, your edits are kept (a `.bak` backup is made).
- **Command aliases**: `/disklens` plus a configurable alias, `/du` by default.
- **PlugMan / PlugManX friendly**: clean unload and load without a restart.
- **One jar for everything**: Spigot, Paper, Purpur and Folia (`folia-supported: true`).

## Commands

Players use a slash (`/du`); in the console and panel console you can omit it (`du`). `/disklens` and `/du` are interchangeable.

| Command | Description |
|---|---|
| `/disklens` | Tree from the server root: depth 2, top 10 per level |
| `/disklens <path> [depth] [topN]` | Tree from a subfolder, e.g. `/disklens world 3 15` |
| `/disklens files [N]` | The N largest files on the server (default 20) |
| `/disklens export [path]` | Full report to `plugins/DiskLens/reports/` |
| `/disklens status` | Whether a scan is running and what is cached |
| `/disklens reload` | Reload `config.yml` and language files |
| `/disklens help` | Help |
| `... -r` | Add to any command to rescan and ignore the cache |

Tip: a folder whose name is only digits needs a trailing slash, e.g. `/disklens 2024/`.

### Permissions

| Permission | Default | Description |
|---|---|---|
| `disklens.use` | op | Use `/disklens` (and its aliases) |

Console and RCON always have access.

### Console and RCON behaviour

- In the console and panel console you get plain text with an ASCII tree; scan progress is written to the log every few seconds.
- RCON and command blocks only receive a reply at the moment the command runs. If a cached result exists, you get the answer immediately. If not (or with `-r`), a scan starts in the background and the result is written to the server log; repeat the command a few seconds later without `-r` to get it over RCON.

## Example console output

Folder names and sizes below are made up for illustration.

```
> disklens
Server root: ./ (server root)  412.3 GB  (18,204 files, 1,236 folders)
+- backups/          180.0 GB  43.7%
|  +- 2026-01-04.zip  60.1 GB  33.4%
|  +- 2026-01-11.zip  60.0 GB  33.3%
|  \- 3 more (59.9 GB)  33.3%
+- world/             96.5 GB  23.4%
|  +- region/          90.2 GB  93.5%
|  \- 5 more (6.3 GB)   6.5%
+- web-map/           88.4 GB  21.4%
+- plugins/           24.1 GB   5.8%
|  \- ExampleLogger/   22.0 GB  91.3%
+- logs/               9.8 GB   2.4%
\- 4 more (13.5 GB)    3.3%
Partition: 412.3 GB used of 500 GB, 87.7 GB free (your hosting quota may differ)
Scan: 3 s ago, took 4.2 s, access errors: 0, skipped links: 0
Rescan: disklens -r   Report to file: disklens export
```

## Installation

1. Download the jar and put it into the server's `plugins/` folder.
2. Start the server (or load the plugin with PlugMan). The console prints `Server root: ...`, the folder DiskLens scans.
3. Run `/disklens` as an operator, or `disklens` in the console.

If you are upgrading from **DiskUsage**, remove the old `DiskUsage-*.jar` first. The permission is now `disklens.use` (was `diskusage.use`) and the data folder is `plugins/DiskLens/`.

## Configuration

`plugins/DiskLens/config.yml` (defaults shown; missing options are added automatically on update):

```yaml
# Do not edit: used to update this file.
config-version: 2

# "auto" = the player's client language, or a code such as "en", "ru".
# Translations live in plugins/DiskLens/lang/<code>.yml; add your own file to add a language.
language: auto

# Language for the console, RCON and players whose language is not available.
default-language: en

# Short command aliases in addition to /disklens. Use [] to disable them.
# Applied on server start / plugin load.
command-aliases: [du]

# How many minutes a scan result is kept (repeated commands and clicks are instant).
cache-minutes: 5

# Defaults for /disklens [path] [depth] [topN]
default-depth: 2
default-top: 10

# Largest files kept per folder (the rest collapses into "N more").
files-per-folder: 50

# Largest files on the whole server kept for /disklens files and the report.
largest-files: 100

# Line limits for one output (anything bigger: /disklens export).
max-chat-lines: 250
max-console-lines: 1000

# Draw the console tree with ASCII characters so it is readable in every hosting panel.
console-ascii-tree: true

# Same for the report file (UTF-8 box-drawing lines by default).
report-ascii-tree: false

# How often scan progress is written to the server log (seconds).
progress-log-seconds: 5
```

## Languages

English and Russian are included. With `language: auto` every player sees DiskLens in their client language; the console, RCON and players with an unsupported language use `default-language`. To add a language, copy `plugins/DiskLens/lang/en.yml` to `<code>.yml` (for example `de.yml`), translate it and run `/disklens reload`. Missing keys are added automatically on update and your edits are kept. Pull requests with translations are welcome.

## PlugMan / PlugManX

DiskLens can be unloaded and loaded without a restart:

```
plugman unload DiskLens
plugman load DiskLens-2.0.0
```

On unload it cancels its tasks, interrupts a running scan (waits up to 5 s), clears the cache and unregisters its command and aliases. A second load creates everything fresh, nothing is duplicated.

**Folia:** with PlugManX 3.0.2, `plugman reload` can print an `UnsupportedOperationException` on Folia. That is a PlugManX issue (it uses the regular Bukkit scheduler internally). Use `unload` followed by `load` instead.

Note: aliases are applied when the plugin loads. If you change `command-aliases`, unload and load the plugin (or restart).

## Supported platforms and versions

- **Platforms:** Spigot, Paper, Purpur, Folia (single jar, `folia-supported: true`).
- **Minecraft:** 1.16.5 and newer (core is Java 8 bytecode; the Folia bridge needs Java 17, which Folia requires anyway).
- **Tested on real servers** (console, RCON, `/du` alias, `files`, `export`, reload, Russian output, clean shutdown):
  - Paper 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.4, 1.21.11
  - Purpur 1.16.5, 1.20.4, 1.21.11
  - Spigot 1.16.5, 1.21.11
  - Folia 1.19.4 (everything works except RCON: Folia 1.19.4 itself rejects all RCON commands), 1.20.6, 1.21.11
  - PlugMan/PlugManX load/unload verified on 1.20.6 and newer (PlugManX 3.0.2 does not enable on older servers).
  - Minecraft 26.x is not tested yet.

## FAQ

**Is it safe on a production server?**
Scanning is async only and never touches the main or region threads. It only reads file metadata. It does not modify or delete anything (except writing reports to `plugins/DiskLens/reports/`).

**Will a scan lag my server?**
It uses a separate async task and reports progress. Very large servers (millions of files) simply take longer; the result is cached.

**Can it delete files?**
No. DiskLens is read-only by design.

**Why do sizes differ from my hosting quota?**
DiskLens sums logical file sizes. The "Partition" line shows the whole disk of the machine, and your hosting quota may be lower.

**Does it follow symlinks?**
No. Symlinks and junctions are skipped (and counted), which prevents loops and escaping the server folder.

**Can I browse outside the server folder?**
No. Paths with `..` and absolute paths outside the root are rejected.

**RCON says "scan started" and gives no tree.**
RCON can only answer at the moment of the command. Wait a few seconds and repeat the command without `-r`.

**A folder with a numeric name does not open.**
Add a trailing slash: `/disklens 2024/`.

**`/du` clashes with another plugin.**
Use `/disklens` or `/disklens:du`, or change/disable `command-aliases` in `config.yml`. DiskLens logs a message if an alias is taken.

**Does it need dependencies?**
No. Zero dependencies.

## Support

Questions and bug reports: see the project links on the right. Please include your server software, Minecraft version and the console output.

---

## По-русски (кратко)

**DiskLens** показывает, какие папки сервера занимают место на диске: дерево по размеру с процентами, топ‑N на уровень, кликабельные папки в чате, обычный текст с ASCII‑деревом для консоли, панели хостинга и RCON. Команда `/disklens` (алиас `/du`), право `disklens.use` (по умолчанию op). Есть `files` (самые большие файлы), `export` (полный отчёт в файл), кеш и `-r` для пересканирования. Сканирование только асинхронное, симлинки не обходятся, выйти за папку сервера нельзя. Языки: английский и русский (игроки получают язык своего клиента). Один jar для Spigot, Paper, Purpur и Folia, Minecraft 1.16.5+. Раньше плагин назывался DiskUsage.
