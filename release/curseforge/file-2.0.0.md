# File: DiskLens 2.0.0

| Field | Value |
|---|---|
| File to upload | `P:\FoliaRust\DiskUsagePlugin\build/libs/DiskLens-2.0.0.jar` (build it first; a plain .jar, Bukkit plugins must be .jar) |
| Display name | `DiskLens 2.0.0` (the version goes in the file name only, not in the project name) |
| Release type | **Release** (a Release or Beta must be approved for the project to sync to the CurseForge App) |
| Game versions | 1.16.5 to 1.21.11 (all PASS in `itest/results/summary-final.md`); not 26.x |
| Changelog format | Markdown (or HTML/text, whichever the form offers) |
| Dependencies | none |

## Changelog (paste)

```markdown
## 2.0.0: DiskUsage is now DiskLens

- Renamed from DiskUsage to **DiskLens**. Command `/disklens` (alias `/du`, configurable via `command-aliases`), permission `disklens.use`.
- One jar for Spigot, Paper, Purpur and Folia, Minecraft 1.16.5+ (core Java 8, Folia bridge Java 17).
- Languages: English and Russian. Players get their client language (`language: auto`); the console uses `default-language`. Add your own file in `plugins/DiskLens/lang/`.
- `config.yml` and language files update automatically: new keys are added and your edits are kept (backup `config.yml.bak`).
- Folder tree sorted by size with percent of parent and top-N per level; clickable folders in chat; plain ASCII tree in the console, hosting panel and RCON.
- `files` for the largest files, `export` for a full text report, cached results, `-r` to rescan.
- Async-only scanning; symlinks are not followed; paths cannot leave the server folder.
- PlugMan / PlugManX safe unload and load.

Upgrading: remove the old DiskUsage jar. The old config is not migrated; DiskLens creates its own `plugins/DiskLens/` folder.
```

Check before pasting: "old config is not migrated" is an assumption from the folder rename (`plugins/DiskLens`). Confirm with the code.
