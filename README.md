<p align="center">
  <img src="release/logo/disklens-icon-256.png" width="128" alt="DiskLens logo">
</p>

<h1 align="center">DiskLens</h1>

<p align="center">
  <b>See what is eating your server's disk, right from chat or the console.</b><br>
  Spigot · Paper · Purpur · Folia · Minecraft 1.16.5+
</p>

<p align="center">
  <a href="README_RU.md">Русская версия</a>
</p>

---

Many hosting panels don't show folder sizes, and you often have no SSH access. DiskLens scans the server folder in the background and shows a size-sorted tree, the largest files, and can write a full report to a file.

## Features

- **Size-sorted folder tree** with sizes, percent of the parent folder and the top N entries per level. Everything else collapses into `… N more (X GB)`.
- **Clickable chat output:** folders are clickable, with buttons to go up, rescan and export.
- **Works everywhere:** server console, hosting panel console and RCON. Console output is plain text with an ASCII tree that renders in any panel.
- **Largest files** across the whole server with `/disklens files`.
- **Full report export** to `plugins/DiskLens/reports/` with no line limit.
- **Non-blocking:** scans run asynchronously and never block the main or region threads. Results are cached (5 min by default); `-r` forces a rescan.
- **Safe:** symbolic links and junctions are never followed, unreadable folders are skipped and counted, and paths cannot leave the server folder (`..` is rejected).
- **Languages:** English and Russian. Players see their client's language, and you can add your own.
- **Self-updating config:** new options are added with their comments, your values stay untouched, and a `.bak` backup is written.
- **PlugMan / PlugManX friendly:** unload, load and update without a restart.
- **One jar for all platforms:** uses Folia's region and async schedulers when Folia is detected.

## Installation

1. Download `DiskLens-<version>.jar` and put it into `plugins/`.
2. Restart the server, or run `plugman load DiskLens-<version>`.
3. Run `/disklens` in game as an operator, or `disklens` in the console.

## Commands

| Command | Description |
|---|---|
| `/disklens` (alias `/du`) | Tree from the server root, depth 2, top 10 per level |
| `/disklens <path> [depth] [topN]` | Tree from a sub-folder, e.g. `/du world 3 15` |
| `/disklens files [N]` | Largest files on the server (default 20) |
| `/disklens export [path]` | Full report to `plugins/DiskLens/reports/report-<date>.txt` |
| `/disklens status` | Scan and cache state |
| `/disklens reload` | Reload `config.yml` and languages |
| `/disklens help` | Help |
| `… -r` | Rescan, ignoring the cache |

To use a folder whose name is only digits, add a slash: `/du 2024/`.

**Permission:** `disklens.use` (operators by default). The console and RCON always have access.

### Console, panels, RCON and command blocks

- **Console:** output is plain text. Scan start, progress (every `progress-log-seconds`) and finish are written to the server log.
- **RCON and command blocks** receive only what is returned during the command call:
  - with a fresh cache, the answer comes back immediately;
  - otherwise the scan runs in the background, the result goes to the log, and repeating the command a few seconds later returns it directly.

## Configuration

```yaml
config-version: 2          # do not edit
language: auto             # players: auto = client language, or a code (en, ru, ...)
default-language: en       # console/RCON and fallback language
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

Translations live in `plugins/DiskLens/lang/<code>.yml`. Copy `en.yml` to a new code, translate it and run `/disklens reload`. Missing keys fall back to English.

## PlugMan / PlugManX

```
plugman unload DiskLens
plugman load DiskLens-<version>
```

On Folia, use unload + load. In PlugManX 3.0.2, `plugman reload` throws `UnsupportedOperationException` inside PlugManX itself, because it calls the Bukkit scheduler, which Folia does not support.

## What usually takes the space

- map render tiles (BlueMap `web/maps`, Dynmap);
- `world*/region`, especially large nether or end pregens;
- backup archives in the server root;
- `plugins/CoreProtect/database.db` (`co purge t:30d`);
- a huge `logs/latest.log`;
- `plugins/spark` profiles and heap dumps.

## Building from source

You need JDK 21 to build (the plugin itself targets Java 8 bytecode for the core and Java 17 for the Folia bridge).

```bash
./gradlew build
```

The jar is written to `build/libs/`.

Integration tests start real servers of several cores and versions:

```bash
python -I itest/run_matrix.py [filter]
```

Results go to `itest/results/`.

## License

<!-- TODO: add a LICENSE file and state the license here. -->
Not yet specified.
