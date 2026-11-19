# DiskLens 2.0.0

The plugin formerly known as **DiskUsage** is now **DiskLens**, with a rewrite for broader platform support.

## Breaking changes (read before upgrading)

- **Renamed** from DiskUsage to DiskLens. Remove the old `DiskUsage-*.jar`.
- Command is now `/disklens`; `/du` stays as an alias (configurable).
- Permission renamed: `diskusage.use` becomes `disklens.use` (default: op).
- Data folder is now `plugins/DiskLens/` (config, lang files, reports).

## New

- **Multi-platform, one jar**: Spigot, Paper, Purpur and Folia, Minecraft 1.16.5 and newer. The core is Java 8 bytecode; the Folia bridge is loaded only on Folia (needs Java 17, as Folia itself does).
- **Multi-core scanning**: faster scans of large servers, always async, never on the main or region threads.
- **Internationalisation**: English and Russian included; players automatically get their client language. Add your own language by dropping a file into `plugins/DiskLens/lang/`.
- **Config and language auto-update**: new options are added on update and your edits are kept (backup saved as `config.yml.bak`).
- **Command aliases**: `command-aliases` in `config.yml` (default `[du]`); if an alias is taken by another plugin, DiskLens says so in the log and `/disklens` still works.
- **PlugMan / PlugManX support**: clean unload and load without a restart; the command and aliases are removed and re-registered, no duplicated tasks.
- **RCON and command blocks**: cached results are answered directly; otherwise the scan starts in the background and the result goes to the log, repeat the command to get it over RCON.
- Plain-text output with an ASCII tree for the console, hosting panels and RCON; clickable folders and buttons in chat.

## Unchanged

- Size-sorted tree with percent of parent and top-N per level, `files`, `export`, result cache and `-r` rescan.
- Symlinks are not followed; paths cannot leave the server folder.
- Read-only: nothing is modified or deleted.

## Upgrade notes

1. Stop the server or `plugman unload DiskUsage`.
2. Delete `DiskUsage-*.jar`, add `DiskLens-2.0.0.jar`.
3. Grant `disklens.use` to staff who had `diskusage.use`.
4. The old `plugins/DiskUsage/` folder is not migrated automatically; you can delete it.
