# Version 2.0.0

| Field | Value |
|---|---|
| Version number | `2.0.0` |
| Version title | `DiskLens 2.0.0` |
| Release channel | **Release**  |
| File to upload | `P:\FoliaRust\DiskUsagePlugin\build\libs\DiskLens-2.0.0.jar` (primary file) |
| Dependencies | none |
| Featured | yes |

## Loaders to tick
Spigot, Paper, Purpur, Folia (and Bukkit if shown/required).

## Game versions to tick

Tick **1.16.5 through 1.21.11** (all 1.16.5, 1.17.x, 1.18.x, 1.19.x, 1.20.x, 1.21.x releases). Do **not** tick 26.x (untested).

Source: `itest/results/summary-final.md` (all PASS; real servers tested at the points below, in-between versions share the same API).

| Platform | Tested versions | Result |
|---|---|---|
| Paper | 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.4, 1.21.11 | PASS |
| Purpur | 1.16.5, 1.20.4, 1.21.11 | PASS |
| Spigot | 1.16.5, 1.21.11 | PASS |
| Folia | 1.20.6, 1.21.11 | PASS |
| Folia | 1.19.4 | PASS except RCON (Folia 1.19.4 itself rejects all RCON commands) |

PlugManX verified on 1.20.6+ only. Folia exists on Modrinth for 1.19.4+ only, so the Folia loader covers those versions.

## Changelog (paste as is)

```markdown
## DiskLens 2.0.0

The plugin formerly known as DiskUsage is now **DiskLens** and runs on more than just Folia.

### New
- One jar for **Spigot, Paper, Purpur and Folia**, Minecraft 1.16.5+.
- Renamed: command `/disklens` (alias `/du`, configurable via `command-aliases`), permission `disklens.use`, data folder `plugins/DiskLens/`.
- **Languages:** English and Russian. Players see their client language (`language: auto`); console uses `default-language`. Add your own `lang/<code>.yml`.
- `config.yml` and language files are **updated automatically** with new keys (backup saved as `config.yml.bak`, your edits are kept).
- Safe with **PlugMan / PlugManX** unload/load.

### Kept
- Size-sorted folder tree with sizes, percent of parent and top-N per level, clickable in chat.
- Plain-text ASCII tree for console, hosting panel and RCON.
- `files`, `export`, result caching (`-r` to rescan), async-only scanning, symlinks not followed, paths cannot leave the server folder.

### Upgrading from DiskUsage 1.x
- Remove the old `DiskUsage-*.jar`. Permission is now `disklens.use` (was `diskusage.use`) and the data folder is `plugins/DiskLens/`; old settings are not migrated.
```

> ⚠ Check the last bullet ("not migrated") against the code before uploading — I did not verify migration behaviour. Also confirm the old permission name (`diskusage.use` per old README).
