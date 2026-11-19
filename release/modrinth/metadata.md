# Modrinth: project metadata — DiskLens

Legend: **[V]** verified on Modrinth docs/API on 2026-11-19; **[A]** assumed (not found in docs, from general knowledge of the site — check in the form).

| Field | Value | Status |
|---|---|---|
| Project type | **Plugin** (choose "Plugin" in the create form; the public API docs list only mod/modpack/resourcepack/shader, but the loaders `bukkit/spigot/paper/purpur/folia` have `plugin` as a supported type) | [V] loaders / [A] form wording |
| Name | `DiskLens` (name only, no filler like "- disk usage plugin") | [V] rule: title = project name only |
| Slug | `disklens` (regex `^[\w!@$()`.+,"\-']{3,64}$`) → `modrinth.com/plugin/disklens` | [V] regex |
| Summary | `See which server folders take up disk space: a size-sorted folder tree for chat, console, panel and RCON.` (103 chars) | [V] "a sentence or two, no formatting, don't repeat title"; numeric limit not in docs, **[A] 256 chars** — we are far below |
| Description | contents of `description.md` | [V] |
| Categories | **Utility**, **Management** (primary). Optional additional: Library is NOT suitable — skip | [V] categories `utility`, `management` exist; [A] that the plugin form offers the same list |
| Loaders | **Spigot, Paper, Purpur, Folia**; add **Bukkit** too if the form requires it (the jar is a standard Bukkit/Spigot plugin) | [V] loader names: `bukkit`, `spigot`, `paper`, `purpur`, `folia` (API `/tag/loader`) |
| Game versions | see `version-2.0.0.md` (1.16.5–1.21.11, no 26.x; from `summary-final.md`) | [V] version ids exist 1.16 … 1.21.11 |
| Environment | **Server-side only** (client: unsupported, server: required) | [V] API values `server_only` / `dedicated_server_only`. The form shows "Client: Unsupported / Server: Required" |
| License | **MIT** (recommended). Pick "MIT License" in the form; SPDX `MIT`. Alternatives: Apache-2.0 (patent clause), GPL-3.0 (copyleft), ARR (source closed). **Decision is the author's** — ask before publishing | [V] SPDX ids accepted; no Modrinth-required license found |
| Source | `https://github.com/<user>/<repo>` — PLACEHOLDER | |
| Issues | `https://github.com/<user>/<repo>/issues` — PLACEHOLDER | |
| Wiki | (empty or README link) — PLACEHOLDER | |
| Discord | (empty) — PLACEHOLDER | |
| Donation links | (empty) | |
| Icon | 512×512 PNG (see checklist) | [A] size; formats png/jpg/webp/gif/svg [V] |
| Visibility on creation | Draft → submit for review | [V] |

## Content disclosures

* No AI-generated art in icon or gallery unless disclosed (Modrinth rules §6: AI-generated gallery/icon/description images may be removed). Use real screenshots.
* Don't claim anything that isn't tested ("works on 1.21.x") — rule §1.7 forbids intentionally misleading claims.

## Verified vs assumed — summary

Verified (docs / API / rules fetched 2026-11-19):
* Loader names `bukkit, spigot, paper, purpur, folia` support the plugin type.
* Slug regex 3–64 characters.
* Version `version_type`: release / beta / alpha; accepted file types .jar etc.
* Rules: title = name only, summary without formatting and not repeating the title, relevant external links, relevant gallery images, accurate metadata, no misleading claims, English description required, headers must not be body text, alt text for images advised, plain-text description required (§2.2).
* Review time: target 24–48 h, in practice weeks possible; plugins can take longer.

Assumed (not found on any docs page):
* Title max 64, summary max 256, body max 65536 chars.
* Icon ≤ 256 KiB, gallery image ≤ 5 MiB.
* Exact label of categories shown for the Plugin type.
