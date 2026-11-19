# DiskLens

**Find out what is eating your server's disk — right from the console, RCON or in-game chat.**

DiskLens scans your server folder and shows a **tree of folders sorted by size**, with the size of every entry and its **percent of the parent folder**. It is built for hosting panels that don't show directory sizes, so you can see in seconds which folder is the culprit.

> Formerly "DiskUsage". Version 2.0.0 is the first release under the name DiskLens.

## Why DiskLens?

Many hosting panels only show a single "disk used" number. When the disk is full you end up guessing: backups? old logs? map tiles? a database? With DiskLens you type one command and get a ranked answer.

* No digging through FTP or running `du` over SSH, which most shared hosts don't give you.
* Works on the **console, the panel console and RCON**, where there are no colours or clickable text. The output is a plain ASCII tree.
* Never blocks the server: scanning is **async only**.
* Typical culprits are easy to spot: web-map tiles, database files, old backups, `.log.gz` archives, crash reports, `.hprof` dumps, and huge world folders.

## Features

* **Size-sorted folder tree** with sizes, percent of parent and a top-N per level. Everything that doesn't make the top-N collapses into one line, e.g. `… 37 more (1.2 GB)`.
* **Clickable in chat** — click a folder to drill into it; buttons for "up", "Rescan" and "Report to file". Coloured by share (red ≥ 50 %, orange ≥ 20 %, yellow ≥ 5 %).
* **Plain text for console / panel / RCON** with an ASCII tree that is readable everywhere.
* `files` — the largest individual files on the whole server.
* `export` — writes a **full, unlimited text report** to `plugins/DiskLens/reports/`.
* **Result caching** (5 minutes by default): repeated commands and clicks are instant. Add `-r` to rescan.
* **Safe by design:** symlinks and junctions are not followed, unreadable folders are skipped and counted, and `..` or absolute paths cannot leave the server folder.
* **Tab completion** of folder names after the first scan.
* **Languages:** English and Russian. Players get their **client language** automatically; the console uses `default-language`. Add your own `lang/<code>.yml` to add a language.
* **Self-updating files:** `config.yml` and language files get new keys added automatically on update (a `config.yml.bak` backup is saved, your edits are kept).
* **PlugMan / PlugManX friendly** — unload/load without a restart.
* **One jar** for Spigot, Paper, Purpur and Folia.

## Example output

Console / panel / RCON (plain text):

```
> disklens
Server root: ./ (server root)   312.4 GB   18,204 files, 1,106 folders
+- world/                       98.1 GB   31.4%
|  +- region/                   97.6 GB   99.5%
|  \- playerdata/               412 MB     0.4%
+- plugins/                     61.3 GB   19.6%
|  +- SomeMapPlugin/            52.0 GB   84.8%
|  +- SomeDatabasePlugin/       8.7 GB    14.2%
|  \- … 12 more (0.6 GB, 1.0%)
+- backups/                     44.0 GB   14.1%
+- logs/                        3.2 GB     1.0%
\- … 9 more (105.8 GB, 33.9%)
Partition: 312.4 GB used of 500 GB, 187.6 GB free (your hosting quota may differ)
Scan: 4 s ago, took 6.1 s, access errors: 0, skipped links: 0
Rescan: disklens -r   Report to file: disklens export
```

*(Names and numbers above are illustrative.)*

## Commands

The main command is `/disklens`; the short alias `/du` is enabled by default (configurable). In the console you can omit the slash.

| Command | What it does |
|---|---|
| `/disklens` | Tree from the server root (depth 2, top 10 per level) |
| `/disklens <path> [depth] [topN]` | Tree of a sub-folder, e.g. `/disklens world 3 15` |
| `/disklens files [N]` | Largest files on the server (default 20) |
| `/disklens export [path]` | Full report to `plugins/DiskLens/reports/` |
| `/disklens status` | Scan state and what is cached |
| `/disklens reload` | Reload `config.yml` and language files |
| `/disklens help` | Help |
| `... -r` | Add to any scan command to rescan and ignore the cache |

Folders with numeric names need a trailing slash: `/disklens 2024/`.

### RCON and command blocks
These only get the reply at the moment the command runs. If the result is cached, it is returned immediately. Otherwise a background scan starts, the result goes to the server log, and repeating the command a few seconds later returns it to RCON.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `disklens.use` | op | Use `/disklens` |

The console and RCON always have access.

## Configuration

`plugins/DiskLens/config.yml` (all options have defaults; missing keys are added automatically):

```yaml
language: auto            # "auto" = player's client language, or a code such as en, ru
default-language: en      # console, RCON and unavailable client languages
command-aliases: [du]     # extra aliases; [] to disable (applied on start/load)
cache-minutes: 5          # how long a scan result is kept
default-depth: 2          # default tree depth
default-top: 10           # children shown per level
files-per-folder: 50      # largest files kept per folder (rest: "N more")
largest-files: 100        # largest files kept for /disklens files and the report
max-chat-lines: 250       # max lines in chat (more: use export)
max-console-lines: 1000   # max lines in console / RCON
console-ascii-tree: true  # ASCII tree in the console
report-ascii-tree: false  # same for the report file (UTF-8 by default)
progress-log-seconds: 5   # how often scan progress is logged
```

## Languages

English (`en`) and Russian (`ru`) are included. With `language: auto` each player sees the plugin in their client language; the console and RCON use `default-language`. Translation files live in `plugins/DiskLens/lang/` — copy `en.yml` to e.g. `de.yml`, translate it, and run `/disklens reload`. Missing keys are filled in on update and your edits are kept.

## PlugMan / PlugManX

DiskLens can be unloaded, loaded and reloaded without a server restart:

```
plugman unload DiskLens
plugman load DiskLens-2.0.0
```

On unload it cancels its tasks, stops a running scan (waits up to 5 seconds), clears the cache and removes its tab-completer. It keeps no static references, threads or listeners, so loading it again creates everything fresh.

**Folia note:** `plugman reload` on Folia may print an `UnsupportedOperationException` from PlugManX itself (it uses the regular Bukkit scheduler, which Folia doesn't support). Use `unload` followed by `load` instead. To update: `plugman unload DiskLens`, replace the jar, `plugman load <new file name without .jar>`.

## Supported platforms and versions

* **Loaders:** Spigot, Paper, Purpur, Folia (`folia-supported: true`) — one jar for all.
* **Minecraft:** built for **1.16.5 and newer** (core compiled for Java 8, Folia support for Java 17).
* **Tested (real servers, integration suite):**
  * Paper 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.4, 1.21.11
  * Purpur 1.16.5, 1.20.4, 1.21.11
  * Spigot 1.16.5, 1.21.11
  * Folia 1.20.6, 1.21.11
  * Folia 1.19.4: works, except RCON — Folia 1.19.4 itself rejects every RCON command (even vanilla `list`/`stop`).
* **PlugMan/PlugManX** was verified on 1.20.6 and newer only (PlugManX 3.0.2 does not enable on older servers).
* Minecraft 26.x has **not** been tested.

## FAQ

**Does it lag my server?**
No. Scanning runs only on an asynchronous scheduler; the main and regional threads are never blocked. Scan progress is shown in the action bar (players) or logged (console).

**Does it follow symlinks?**
No. Symlinks and junctions are not followed, and are counted as "skipped links". Folders it can't read are skipped and counted as "access errors".

**Can it read files outside my server folder?**
No. Paths with `..` and absolute paths outside the server root are rejected.

**The sizes don't match my hosting panel.**
DiskLens shows logical sizes (the sum of file sizes). The "Partition" line shows the whole machine's disk, and your hosting quota may be lower or counted differently.

**Does it delete anything?**
No. DiskLens is read-only; it only writes reports to `plugins/DiskLens/reports/` when you ask for `export`.

**Why does `/du` not work?**
Another plugin may already own that alias. Use `/disklens` or `/disklens:du`, or change `command-aliases` in the config.

**Do I need a database or any dependency?**
No. Drop the jar in `plugins/`.

**Which Java do I need?**
Whatever your server already runs; the core is Java 8 compatible and the Folia part needs Java 17 (which Folia requires anyway).

## Installation

1. Download the jar and put it in your server's `plugins/` folder (replace an old `DiskUsage` jar if you have one).
2. Restart the server, or load it with PlugMan.
3. Run `/disklens`.

## Support

Found a bug or have an idea? Open an issue on the issue tracker (see the links on this page).
