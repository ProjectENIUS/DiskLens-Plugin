#!/usr/bin/env python3
"""DiskLens release uploader (Modrinth / CurseForge / Hangar / GitHub).

Python 3.10+, standard library only.  DRY-RUN BY DEFAULT: nothing is sent
unless --yes is given.  Tokens are read ONLY from environment variables
(MODRINTH_TOKEN, CURSEFORGE_TOKEN, HANGAR_API_KEY, GITHUB_TOKEN), are never
printed (redacted) and never written anywhere.

Usage:  python publish.py {modrinth|curseforge|hangar|github|all} [--yes] [--config release.json]

API facts verified from official docs on 2026-11-19 (via page fetch):
 * Modrinth (docs.modrinth.com/api): POST https://api.modrinth.com/v2/version,
   multipart: part "data" (JSON: project_id, file_parts, primary_file, name,
   version_number, changelog, dependencies, game_versions, version_type,
   loaders, featured, status) + file parts named in file_parts.
   Header "Authorization: <mrp_ token>" (no Bearer), scope VERSION_CREATE;
   unique User-Agent required. PATCH /v2/project/{id|slug} {"body": ...}
   (scope PROJECT_WRITE, 204). The "/v2" prefix is inferred from the docs'
   migration notes (not printed on the createversion page itself).
 * CurseForge (support.curseforge.com Upload API article):
   POST {site}/api/projects/{id}/upload-file, multipart parts "metadata"
   (JSON: changelog, changelogType, displayName, gameVersions[ids],
   releaseType, isMarkedForManualRelease) and "file"; header X-Api-Token;
   GET /api/game/versions -> [{id, gameVersionTypeID, name, slug}].
 * Hangar (hangar.papermc.io/v3/api-docs OpenAPI): POST /api/v1/authenticate
   ?apiKey=KEY -> {token, expiresIn}; Authorization: Bearer <JWT>;
   POST /api/v1/projects/{slugOrId}/upload multipart parts "versionUpload"
   (JSON: version, channel, description, platformDependencies{PAPER:[..]},
   pluginDependencies, files[{platforms:[PAPER]}]) and "files" (jar).
   Platform enum: PAPER, WATERFALL, VELOCITY. Key needs create_version.
   NOT fully verified: the authenticate/upload path details came from a
   summary of the spec (the middle of the spec was not read verbatim);
   the API-key-as-query-param form is the least certain point.
 * GitHub (docs.github.com REST): POST /repos/{o}/{r}/releases
   {tag_name,target_commitish,name,body,draft,prerelease}; assets: POST
   https://uploads.github.com/repos/{o}/{r}/releases/{id}/assets?name=FILE,
   raw body. `gh release create` flags are from general knowledge, not re-read.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
UA = "DiskLens-release-script/2.0.0"
ENV_VARS = {
    "modrinth": "MODRINTH_TOKEN",
    "curseforge": "CURSEFORGE_TOKEN",
    "hangar": "HANGAR_API_KEY",
    "github": "GITHUB_TOKEN",
}


def load_dotenv(path: Path) -> None:
    """Minimal .env reader: KEY=VALUE lines; never overrides variables already set."""
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        k, v = k.strip(), v.strip().strip('"').strip("'")
        if k in ENV_VARS.values() and v and not os.environ.get(k):
            os.environ[k] = v


class Problem(Exception):
    """Fatal validation error for a target."""


def redact(text: str) -> str:
    """Remove any env token value from text (defence in depth)."""
    for var in ENV_VARS.values():
        val = os.environ.get(var)
        if val and len(val) >= 4:
            text = text.replace(val, "<REDACTED>")
    return text


def say(msg: str = "") -> None:
    out = redact(msg)
    try:
        print(out)
    except UnicodeEncodeError:
        print(out.encode("ascii", "replace").decode())


def token(target: str, yes: bool) -> str | None:
    var = ENV_VARS[target]
    val = os.environ.get(var)
    if not val:
        if yes:
            raise Problem(f"environment variable {var} is not set")
        say(f"  [warn] {var} is not set (required with --yes)")
        return None
    say(f"  token: {var} is set (value hidden)")
    return val


def rel(cfg_dir: Path, p: str) -> Path:
    return (cfg_dir / p).resolve()


def need_file(p: Path, what: str) -> Path:
    if not p.is_file():
        raise Problem(f"{what} not found: {p}")
    say(f"  {what}: {p} ({p.stat().st_size} bytes)")
    return p


def check_placeholder(label: str, val: str, yes: bool) -> None:
    if not val or val.startswith("REPLACE"):
        if yes:
            raise Problem(f"{label} in release.json is still a placeholder")
        say(f"  [warn] {label} is a placeholder ({val!r}): fill it in release.json before --yes")


def multipart(parts: list[tuple]) -> tuple[bytes, str]:
    """parts: (name, filename|None, content_type|None, bytes)."""
    boundary = "----DiskLens" + uuid.uuid4().hex
    out = bytearray()
    for name, fname, ctype, data in parts:
        out += f"--{boundary}\r\n".encode()
        disp = f'Content-Disposition: form-data; name="{name}"'
        if fname:
            disp += f'; filename="{fname}"'
        out += (disp + "\r\n").encode()
        if ctype:
            out += f"Content-Type: {ctype}\r\n".encode()
        out += b"\r\n" + data + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return bytes(out), f"multipart/form-data; boundary={boundary}"


def http(method: str, url: str, headers: dict, body: bytes | None = None) -> tuple[int, bytes]:
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def show_request(method: str, url: str, headers: dict, parts: str = "") -> None:
    say(f"  {method} {url}")
    for k, v in headers.items():
        shown = "<REDACTED>" if k.lower() in ("authorization", "x-api-token") and "<" not in v else v
        say(f"    {k}: {shown}")
    if parts:
        say(parts)


def check_ok(status: int, body: bytes, what: str) -> bytes:
    if not 200 <= status < 300:
        raise Problem(f"{what} failed: HTTP {status}: {redact(body.decode('utf-8', 'replace'))[:800]}")
    return body


def pretty(obj: dict, indent: str = "      ") -> str:
    return json.dumps(obj, indent=2, ensure_ascii=False).replace("\n", "\n" + indent)


# ---------------------------------------------------------------- Modrinth
def do_modrinth(cfg: dict, d: Path, yes: bool) -> None:
    m = cfg["modrinth"]
    jar = need_file(rel(d, cfg["jar"]), "jar")
    changelog = need_file(rel(d, cfg["changelog_file"]), "changelog").read_text(encoding="utf-8")
    body_text = None
    if m.get("update_body"):
        body_text = need_file(rel(d, m["body_file"]), "project body").read_text(encoding="utf-8")
    tok = token("modrinth", yes)
    check_placeholder("modrinth.project", m["project"], yes)
    base = "https://api.modrinth.com/v2"
    headers = {"Authorization": tok or "", "User-Agent": UA}
    data = {
        "project_id": m["project"],  # must be the ID; a slug is resolved to the ID on --yes
        "name": cfg["version_name"],
        "version_number": cfg["version"],
        "changelog": changelog,
        "dependencies": [],
        "game_versions": cfg["game_versions"],
        "version_type": cfg["release_type"],
        "loaders": m["loaders"],
        "featured": m["featured"],
        "status": "listed",
        "file_parts": ["file"],
        "primary_file": "file",
    }
    if not yes:
        say("[modrinth] DRY-RUN. Would send:")
        if body_text is not None:
            show_request("PATCH", f"{base}/project/{m['project']}", {**headers, "Content-Type": "application/json"},
                         f"    body: {{\"body\": <{m['body_file']}, {len(body_text)} chars>}}")
        show_request("POST", f"{base}/version", {**headers, "Content-Type": "multipart/form-data"},
                     "    part 'data' (application/json): " + pretty({**data, "changelog": f"<{len(changelog)} chars>"})
                     + f"\n    part 'file' (application/java-archive): {jar.name}")
        return
    st, b = http("GET", f"{base}/project/{urllib.parse.quote(m['project'])}", headers)
    data["project_id"] = json.loads(check_ok(st, b, "project lookup"))["id"]
    if body_text is not None:
        st, b = http("PATCH", f"{base}/project/{data['project_id']}",
                     {**headers, "Content-Type": "application/json"}, json.dumps({"body": body_text}).encode())
        check_ok(st, b, "project body update")
        say("  project body updated")
    payload, ctype = multipart([
        ("data", None, "application/json", json.dumps(data).encode()),
        ("file", jar.name, "application/java-archive", jar.read_bytes()),
    ])
    st, b = http("POST", f"{base}/version", {**headers, "Content-Type": ctype}, payload)
    v = json.loads(check_ok(st, b, "version create"))
    say(f"[modrinth] created version {v.get('id')}")


# -------------------------------------------------------------- CurseForge
def do_curseforge(cfg: dict, d: Path, yes: bool) -> None:
    c = cfg["curseforge"]
    jar = need_file(rel(d, cfg["jar"]), "jar")
    changelog = need_file(rel(d, c["changelog_file"]), "changelog").read_text(encoding="utf-8")
    tok = token("curseforge", yes)
    check_placeholder("curseforge.project_id", c["project_id"], yes)
    base = c["base_url"].rstrip("/")
    headers = {"X-Api-Token": tok or "", "User-Agent": UA}
    ids: list[int] | None = None
    if tok:  # GET is read-only, so it is also done in dry-run to validate the mapping
        st, b = http("GET", f"{base}/api/game/versions", headers)
        by_name: dict[str, list[dict]] = {}
        for v in json.loads(check_ok(st, b, "game versions")):
            by_name.setdefault(v["name"], []).append(v)
        missing = [n for n in cfg["game_versions"] if n not in by_name]
        if missing:
            raise Problem("CurseForge has no game version named: " + ", ".join(missing))
        ids = []
        # Names repeat across version types. Type 1 is the Bukkit list (checked 2026-11-19:
        # it contains "CB 1060"-style CraftBukkit builds); 615 is Bedrock Addons, the
        # per-release types (e.g. 77784 "Minecraft 1.21") are for mods.
        want = c.get("bukkit_type_id", 1)
        for n in cfg["game_versions"]:
            cands = [x for x in by_name.get(n, []) if x["gameVersionTypeID"] == want]
            if not cands and n.count(".") == 2:
                # The Bukkit list has no 1.16.5 / 1.17.1: it only has "1.16", "1.17".
                parent = n.rsplit(".", 1)[0]
                cands = [x for x in by_name.get(parent, []) if x["gameVersionTypeID"] == want]
                if cands:
                    say(f"  [info] {n} is not in the Bukkit list, using {parent}")
            if not cands:
                raise Problem(f"CurseForge has no Bukkit (type {want}) game version for {n}")
            if cands[0]["id"] not in ids:
                ids.append(cands[0]["id"])
        say(f"  mapped {len(ids)} game versions to ids (Bukkit type {want})")
    else:
        say("  [info] CURSEFORGE_TOKEN not set: GET /api/game/versions skipped, name->id mapping NOT validated")
    meta = {
        "changelog": changelog,
        "changelogType": c["changelog_type"],
        "displayName": cfg["version_name"],
        "gameVersions": ids if ids is not None else "<ids resolved via GET /api/game/versions at run time>",
        "releaseType": cfg["release_type"],
    }
    url = f"{base}/api/projects/{c['project_id']}/upload-file"
    if not yes:
        say("[curseforge] DRY-RUN. Would send:")
        show_request("POST", url, {**headers, "Content-Type": "multipart/form-data"},
                     "    part 'metadata' (application/json): " + pretty({**meta, "changelog": f"<{len(changelog)} chars>"})
                     + f"\n    part 'file': {jar.name}")
        return
    payload, ctype = multipart([
        ("metadata", None, "application/json", json.dumps(meta).encode()),
        ("file", jar.name, "application/java-archive", jar.read_bytes()),
    ])
    st, b = http("POST", url, {**headers, "Content-Type": ctype}, payload)
    say(f"[curseforge] uploaded: {check_ok(st, b, 'upload').decode()}")


# ------------------------------------------------------------------ Hangar
def do_hangar(cfg: dict, d: Path, yes: bool) -> None:
    h = cfg["hangar"]
    jar = need_file(rel(d, cfg["jar"]), "jar")
    if jar.stat().st_size > 10_000_000:
        raise Problem("jar exceeds Hangar's 10 MB limit")
    changelog = need_file(rel(d, cfg["changelog_file"]), "changelog").read_text(encoding="utf-8")
    key = token("hangar", yes)
    check_placeholder("hangar.slug", h["slug"], yes)
    base = "https://hangar.papermc.io/api/v1"
    vers = h.get("hangar_versions") or cfg["game_versions"]
    ver_upload = {
        "version": cfg["version"],
        "channel": h["channel"],
        "description": changelog,
        "platformDependencies": {h["platform"]: vers},
        "pluginDependencies": {},
        "files": [{"platforms": [h["platform"]]}],
    }
    base_h = {"User-Agent": UA}
    url = f"{base}/projects/{urllib.parse.quote(h['slug'])}/upload"
    if not yes:
        say("[hangar] DRY-RUN. Would send:")
        show_request("POST", f"{base}/authenticate?apiKey=<REDACTED>", base_h, "    -> {token, expiresIn}")
        show_request("POST", url, {**base_h, "Authorization": "Bearer <JWT>", "Content-Type": "multipart/form-data"},
                     "    part 'versionUpload' (application/json): " + pretty({**ver_upload, "description": f"<{len(changelog)} chars>"})
                     + f"\n    part 'files' (application/java-archive): {jar.name}")
        say("  note: if Hangar rejects a version as unsupported, set hangar.hangar_versions in release.json to the list Hangar offers.")
        return
    st, b = http("POST", f"{base}/authenticate?apiKey={urllib.parse.quote(key)}", base_h, b"")
    jwt = json.loads(check_ok(st, b, "authenticate"))["token"]
    payload, ctype = multipart([
        ("versionUpload", None, "application/json", json.dumps(ver_upload).encode()),
        ("files", jar.name, "application/java-archive", jar.read_bytes()),
    ])
    st, b = http("POST", url, {**base_h, "Authorization": f"Bearer {jwt}", "Content-Type": ctype}, payload)
    say(f"[hangar] uploaded: {check_ok(st, b, 'upload').decode()}")


# ------------------------------------------------------------------ GitHub
SETUP = r"""  GitHub setup needed first (this script does NOT do it for you):
    cd P:\FoliaRust\DiskUsagePlugin
    git init -b main
    (check .gitignore first: do not commit build/, itest server data, tokens)
    git add .
    git commit -m "DiskLens 2.0.0"
    # create an EMPTY repo on github.com (web UI), then:
    git remote add origin https://github.com/OWNER/NAME.git
    git push -u origin main
  then set github.repo in release.json to OWNER/NAME."""


def git(*args: str, cwd: Path) -> tuple[int, str]:
    try:
        r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
        return r.returncode, (r.stdout + r.stderr).strip()
    except FileNotFoundError:
        return 127, "git not installed"


def do_github(cfg: dict, d: Path, yes: bool) -> None:
    g = cfg["github"]
    jar = need_file(rel(d, cfg["jar"]), "jar")
    icon = need_file(rel(d, cfg["icon_file"]), "icon")
    changelog_path = need_file(rel(d, cfg["changelog_file"]), "changelog")
    notes = changelog_path.read_text(encoding="utf-8")
    tok = token("github", yes)
    check_placeholder("github.repo", g["repo"], yes)
    repo_dir = rel(d, "../..")
    blockers = []
    rc, _ = git("rev-parse", "--is-inside-work-tree", cwd=repo_dir)
    if rc != 0:
        blockers.append("project is not a git repository")
    else:
        rc, out = git("remote", "get-url", "origin", cwd=repo_dir)
        if rc != 0:
            blockers.append("git repo has no 'origin' remote")
        else:
            say(f"  origin: {out}")
        rc, _ = git("rev-parse", "--verify", "HEAD", cwd=repo_dir)
        if rc != 0:
            blockers.append("repo has no commits yet")
    gh = shutil.which("gh")
    say(f"  gh CLI: {gh or 'not installed, the REST API would be used'}")
    cmd = ["gh", "release", "create", g["tag"], str(jar), str(icon), "--repo", g["repo"],
           "--title", g["title"], "--notes-file", str(changelog_path)]
    if gh:
        say("  would run: " + " ".join(cmd) + "   (gh reads GITHUB_TOKEN from the environment)")
    else:
        say(f"  would POST https://api.github.com/repos/{g['repo']}/releases "
            f"{{tag_name: {g['tag']!r}, name: {g['title']!r}, body: <{len(notes)} chars>}}")
        say(f"  then POST https://uploads.github.com/repos/{g['repo']}/releases/<id>/assets?name={jar.name}  and  ?name={icon.name}")
        say("    headers: Authorization: Bearer <REDACTED>, Accept: application/vnd.github+json, X-GitHub-Api-Version: 2022-11-28")
    if blockers:
        say("[github] BLOCKED: " + "; ".join(blockers))
        say(SETUP)
        if yes:
            raise Problem("cannot release: " + "; ".join(blockers))
        return
    rc, _ = git("ls-remote", "--exit-code", "--tags", "origin", g["tag"], cwd=repo_dir)
    if rc == 0:
        raise Problem(f"tag {g['tag']} already exists on origin")
    if not yes:
        say("[github] DRY-RUN done. The tag is created by GitHub on the default branch head: push your commit first.")
        return
    if gh:
        r = subprocess.run(cmd)
        if r.returncode:
            raise Problem(f"gh exited with code {r.returncode}")
        return
    api = {"Authorization": f"Bearer {tok}", "Accept": "application/vnd.github+json",
           "X-GitHub-Api-Version": "2022-11-28", "User-Agent": UA}
    body = json.dumps({"tag_name": g["tag"], "name": g["title"], "body": notes}).encode()
    st, b = http("POST", f"https://api.github.com/repos/{g['repo']}/releases",
                 {**api, "Content-Type": "application/json"}, body)
    rid = json.loads(check_ok(st, b, "create release"))["id"]
    for f, ct in ((jar, "application/java-archive"), (icon, "image/png")):
        st, b = http("POST", f"https://uploads.github.com/repos/{g['repo']}/releases/{rid}/assets?name={urllib.parse.quote(f.name)}",
                     {**api, "Content-Type": ct}, f.read_bytes())
        check_ok(st, b, f"upload {f.name}")
    say("[github] release created")


TARGETS = {"modrinth": do_modrinth, "curseforge": do_curseforge, "hangar": do_hangar, "github": do_github}


def main() -> int:
    ap = argparse.ArgumentParser(description="DiskLens release uploader (dry-run by default)")
    ap.add_argument("target", choices=[*TARGETS, "all"])
    ap.add_argument("--config", default=str(HERE / "release.json"))
    ap.add_argument("--yes", action="store_true", help="actually send (default: dry-run)")
    ap.add_argument("--dry-run", action="store_true", help="explicit dry-run (this is the default)")
    a = ap.parse_args()
    if a.yes and a.dry_run:
        ap.error("--yes and --dry-run are mutually exclusive")
    load_dotenv(HERE / ".env")
    cfg_path = Path(a.config).resolve()
    with open(cfg_path, encoding="utf-8") as f:
        cfg = json.load(f)
    d = cfg_path.parent
    say(("*** LIVE RUN ***" if a.yes else "DRY-RUN (nothing is sent)") + f"  {cfg['name']} {cfg['version']}")
    names = list(TARGETS) if a.target == "all" else [a.target]
    failed = []
    for n in names:
        say(f"\n=== {n} ===")
        try:
            TARGETS[n](cfg, d, a.yes)
        except Problem as e:
            say(f"[{n}] ERROR: {e}")
            failed.append(n)
        except (urllib.error.URLError, OSError, KeyError, json.JSONDecodeError) as e:
            say(f"[{n}] ERROR: {type(e).__name__}: {e}")
            failed.append(n)
    say("\nResult: " + ("FAILED: " + ", ".join(failed) if failed else "OK"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
