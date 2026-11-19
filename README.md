# DiskLens

**See what is eating your server's disk — right from chat or the console.**

Many hosting panels don't show folder sizes, and you often have no SSH. DiskLens scans the server folder in the background and shows a tree sorted by size, the largest files, and can write a full report to a file.

[Русская версия](README.ru.md)

## Features

- A folder tree sorted by size, with sizes, percent of the parent folder and the top N entries per level. Everything else collapses into `… N more (X GB)`.
- In chat, folders are **clickable**, and there are buttons for going up, rescanning and exporting.
- Works fully from the **server console, hosting panel console and RCON**. Output there is plain text without colour codes, with an ASCII tree that renders in any panel.
- `files` lists the largest files on the whole server.
- `export` writes the full, unlimited report to `plugins/DiskLens/reports/`.
- Scanning runs asynchronously and never blocks the main or region threads. Results are cached (5 min by default), `-r` forces a rescan.
- It is safe:
  - symbolic links and junctions are never followed;
  - unreadable folders are skipped and counted;
  - paths can't leave the server folder (`..` is rejected).
- **Languages:** English and Russian. Players get their client's language, and you can add your own language file.
- **Config and language files update themselves.** New options are appended with their comments, your values stay untouched, and a `.bak` backup is written.
- **PlugMan / PlugManX friendly:** unload, load and update without a restart. There are no static leftovers, scans are cancelled and aliases are removed.
- **One jar for Spigot, Paper, Purpur and Folia, Minecraft 1.16.5+.** It uses Folia's region and async schedulers when Folia is detected.

## Installation

1. Put `DiskLens-<version>.jar` into `plugins/`.
2. Restart the server, or run `plugman load DiskLens-<version>`.
3. Run `/disklens` in game as an operator, or `disklens` in the console.

## Commands

| Command | Description |
|---|---|
| `/disklens` (alias `/du`) | Tree from the server root, depth 2, top 10 per level |
| `/disklens <path> [depth] [topN]` | Tree from a sub-folder, e.g. `/du world 3 15` |
| `/disklens files [N]` | Largest files on the server (default 20) |
| `/disklens export [path]` | Full report to `plugins/DiskLens/reports/report-<date>.txt` |
| `/disklens status` | Scan / cache state |
| `/disklens reload` | Reload `config.yml` and languages |
| `/disklens help` | Help |
| `… -r` | Rescan, ignoring the cache |

Write folders whose names are only digits with a slash: `/du 2024/`.

Permission: `disklens.use`, OP by default. The console and RCON always have it.

### Console, panels, RCON and command blocks

- **Console:** output is plain text. Scan start, progress (every `progress-log-seconds`) and finish are written to the server log.
- **RCON and command blocks** only receive what is sent during the command call:
  - with a fresh cache, the answer is returned immediately;
  - otherwise the scan starts in the background, the result goes to the log, and repeating the command a few seconds later returns it directly.

## Configuration

```yaml
config-version: 2          # do not edit
language: auto             # players: auto = client language, or a code (en, ru, ...)
default-language: en       # console/RCON and fallback language (auto = system language)
command-aliases: [du]      # extra short commands, [] to disable
cache-minutes: 5
default-depth: 2
default-top: 10
files-per-folder: 50       # largest files kept per folder, the rest become "N more"
largest-files: 100         # largest files kept for /disklens files and the report
max-chat-lines: 250
max-console-lines: 1000
console-ascii-tree: true   # +- \- | instead of ├─ └─ │ in the console
report-ascii-tree: false
progress-log-seconds: 5
```

Translations live in `plugins/DiskLens/lang/<code>.yml`. Copy `en.yml` to a new code, translate it and run `/disklens reload`. Missing keys always fall back to English.

## PlugMan / PlugManX

```
plugman unload DiskLens
plugman load DiskLens-<version>
```

On Folia, use unload + load. In PlugManX 3.0.2, `plugman reload` throws `UnsupportedOperationException` inside PlugManX itself, because it calls the Bukkit scheduler, which Folia does not support.

## What usually takes the space

- map render tiles (`bluemap/web/maps`, Dynmap);
- `world*/region`, especially large nether or end pregens;
- backup archives in the server root;
- `plugins/CoreProtect/database.db` (`co purge t:30d`);
- a huge `logs/latest.log`;
- `plugins/spark` profiles and heap dumps.

## Building

You need JDK 21 to build (the plugin itself targets Java 8). Run `gradlew build`. The jar is in `build/libs/`.

Integration tests start real servers of several cores and versions: `python -I itest/run_matrix.py [filter]`. Results go to `itest/results/`.
