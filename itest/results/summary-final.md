# DiskLens 2.0.0 — integration test results (final)

Each case starts a real server with DiskLens and checks:
- config/lang auto-update;
- the console tree (ASCII, no colour codes), the `/du` alias, `files`, `export`, the path guard, `status`;
- RCON tree/files/path guard;
- `reload` + Russian output;
- the PlugManX unload/load cycle (incl. unload during a scan), where PlugManX runs;
- no DiskLens errors, clean shutdown.

| Core | Version | Java | Result |
|---|---|---|---|
| Paper | 1.16.5 | 8 | PASS (PlugManX n/a) |
| Paper | 1.17.1 | 17 | PASS (PlugManX n/a) |
| Paper | 1.18.2 | 17 | PASS (PlugManX n/a) |
| Paper | 1.19.4 | 17 | PASS (PlugManX n/a) |
| Paper | 1.20.6 | 21 | PASS |
| Paper | 1.21.4 | 21 | PASS |
| Paper | 1.21.11 | 21 | PASS |
| Purpur | 1.16.5 | 8 | PASS (PlugManX n/a) |
| Purpur | 1.20.4 | 17 | PASS (PlugManX n/a) |
| Purpur | 1.21.11 | 21 | PASS |
| Spigot | 1.16.5 | 16 | PASS (PlugManX n/a) |
| Spigot | 1.21.11 | 21 | PASS |
| Folia | 1.19.4 | 17 | PASS except RCON: Folia 1.19.4 itself rejects every RCON command (vanilla `list`/`stop` too) |
| Folia | 1.20.6 | 21 | PASS |
| Folia | 1.21.11 | 21 | PASS |

"PlugManX n/a": PlugManX 3.0.2 does not enable on these servers, so the unload/load cycle was not run there.
Not covered by automation: Minecraft 26.x (needs a newer Java than installed), in-game clicks/hover (a visual bot test is in progress).
