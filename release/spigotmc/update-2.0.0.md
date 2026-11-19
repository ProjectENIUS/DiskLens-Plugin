# Первый пост обновления (Resource Update)

**Version string:** `2.0.0`
**Update title:** `DiskLens 2.0.0 - first release on SpigotMC`

Для первой публикации ресурса SpigotMC сам просит файл и версию в форме создания; текст ниже вставьте как описание/changelog первой версии (при последующих обновлениях пост «Post Resource Update» с новой версией).

## Текст (BBCode)

```
[B]DiskLens 2.0.0[/B] - first public release (the plugin was previously known as DiskUsage).

[B]New[/B]
[LIST]
[*]Renamed from DiskUsage to [B]DiskLens[/B]; command [ICODE]/disklens[/ICODE], alias [ICODE]/du[/ICODE] (configurable), permission [ICODE]disklens.use[/ICODE].
[*]One jar for [B]Spigot, Paper, Purpur and Folia[/B], Minecraft 1.16.5+ (Java 8 core, Folia bridge on Java 17).
[*]Folder tree sorted by size with percent of parent and top-N per level; the rest collapses into "N more".
[*]Clickable folders in chat; plain-text ASCII tree for the console, hosting panels and RCON.
[*][ICODE]files[/ICODE] (largest files), [ICODE]export[/ICODE] (full text report), [ICODE]status[/ICODE], [ICODE]reload[/ICODE], [ICODE]-r[/ICODE] to rescan.
[*]Scan result cache; async-only scanning; symlinks are not followed; paths cannot leave the server folder.
[*]English and Russian; players get their client language ([ICODE]language: auto[/ICODE]).
[*][ICODE]config.yml[/ICODE] and language files update themselves (new keys added, your edits kept, [ICODE]config.yml.bak[/ICODE] saved).
[*]Safe to unload / load with PlugMan / PlugManX.
[/LIST]

[B]Upgrading from DiskUsage 1.x[/B]
Remove the old jar and put DiskLens-2.0.0.jar into plugins/. The old plugin folder and permission ([ICODE]diskusage.use[/ICODE]) are not reused: grant [ICODE]disklens.use[/ICODE] if you gave the old permission to non-ops.
```

> Проверьте перед публикацией: действительно ли старая папка `plugins/DiskUsage` и право `diskusage.use` не мигрируются (по plugin.yml право теперь только `disklens.use`; миграции старого конфига в коде я не проверял). Если это не так, поправьте последний абзац.
