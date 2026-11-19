# DiskLens: Hangar metadata

Legend: **[V]** verified on hangar.papermc.io on 2026-11-19 (guidelines page, public API `/api/internal/data/validations`, `/api/v1/projects`). **[A]** assumed or from memory; check in the Hangar UI while creating the project.

## Project

| Field | Value | Notes |
|---|---|---|
| Owner | your personal Hangar user (or an org) | The slug URL becomes `hangar.papermc.io/<owner>/DiskLens` |
| Name | `DiskLens` | [V] 3-28 chars, only letters, digits, `-`, `_`. [V] must not contain a platform name, version or tagline (so no "DiskLens-Paper"). It must also be unique: check availability. |
| Slug | `DiskLens` | [A] derived from the name automatically |
| Summary (description field) | `See which server folders eat your disk: size-sorted tree in chat, console, panel and RCON.` | [V] limit is **120** characters. This text is 91. |
| Category | **Admin Tools** | [V] `admin_tools` exists as a category value. [A] the other values (Chat, Developer Tools, Economy, Gameplay, Games, Protection, Role Playing, World Management, Miscellaneous) are from memory; Admin Tools fits best. |
| Keywords / tags | `disk`, `storage`, `diskusage`, `folia`, `admin` | [V] max 5 keywords, each up to 16 chars. A separate "tags" list (settings.tags) also exists; [A] Hangar offers a fixed set there (e.g. "Supports Folia"): tick it if shown. |
| Main page | contents of `description.md` | [V] page content min 15, max 75 000 chars. |
| Icon | 512x512 PNG, see `checklist.md` | [A] no limits found in guidelines; square PNG/JPG is typical |
| License | **MIT** (recommended) | [V] license name field max 20 chars, allowed chars letters, digits, `-_.() +`. Hangar also offers a type list [A]; if MIT is not in the dropdown choose "Other" and type `MIT`. **Ask the user to confirm the licence** (no LICENSE file was checked here). Alternatives: Apache-2.0, GPL-3.0 (copyleft), All Rights Reserved. |
| Visibility | Public | |
| Donation / sponsors | leave empty | [V] sponsors only for real donors, kept secondary |

## Links section (placeholders, fill in yourself)

Hangar groups links under "Top"/"Sidebar" style sections [A]. URLs must be valid http(s) [V].

| Link title | URL |
|---|---|
| Source | `https://github.com/<YOUR-USER>/DiskLens` (placeholder, only if a public repo exists) |
| Issues | `https://github.com/<YOUR-USER>/DiskLens/issues` (placeholder) |
| Support / Discord | `<YOUR-DISCORD-INVITE>` (placeholder, optional) |
| Wiki / Docs | `<optional>` |

Links to free related resources, docs, source and support Discords are allowed [V]. Advertising is not.

## Version (first release)

| Field | Value |
|---|---|
| Version name | `2.0.0` | [V] max 32 chars, allowed `a-zA-Z0-9-_.+` |
| Channel | `Release` (the default channel on a new project [V]; limit 5 channels per project [V]) |
| File | `build/libs/DiskLens-2.0.0.jar` | [V] max file size **10 MB** (10 000 000 bytes). Check the jar size before uploading. |
| Changelog | contents of `changelog-2.0.0.md` |
| Dependencies | **none** (leave platform dependency lists empty: no required, no optional plugins) |

### Platforms and version ranges

[V] Hangar's own platform list is Paper, Velocity and Waterfall (the API shows `PAPER` for plugins). The guidelines page says nothing about Folia, Spigot or Bukkit. [A] Folia is not a separate platform: Folia-capable plugins are uploaded under **Paper**, and Folia support is declared by `folia-supported: true` in `plugin.yml` (already present) and by the "Supports Folia" tag if the UI offers it. Spigot-only servers are not a Hangar platform, so say it in the description (already done).

| Platform | Versions to tick | Status |
|---|---|---|
| Paper | 1.16.5 to 1.21.11 (tick every version Hangar lists in this range) | Per `itest/results/summary-final.md`: Paper 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.4, 1.21.11 PASS; Purpur and Spigot 1.16.5 to 1.21.11 PASS; Folia 1.20.6 to 1.21.11 PASS, Folia 1.19.4 PASS except RCON. Do **not** tick 26.x (untested). Hangar may not list 1.16.5 to 1.18.x [A]: tick what exists. |
| Velocity | not applicable | |
| Waterfall | not applicable | |

Guidelines [V]: a selected platform must be able to load the plugin when placed in its plugin folder. The plugin's `api-version` is `1.13` and the core targets Java 8; Hangar may warn that the old version range is unsupported; [A] older versions might not be listed.

## Hangar rules that apply to this project [V]

- Name must be original and not imply an official PaperMC project.
- No obfuscation (the jar is not obfuscated).
- No hidden paywall or advertising.
- Do not push needless versions only to bump the update date.
- It is a renamed continuation of the author's own plugin DiskUsage, so it is not a "fork" of a third party.
