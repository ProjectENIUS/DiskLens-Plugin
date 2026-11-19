#!/usr/bin/env python3
"""
DiskLens integration test matrix.

For every (core, version) case: download the server jar (cached), start a throwaway server on a free port with
DiskLens (+ PlugManX if present), drive commands through the console and RCON, check the server log, stop.
Writes itest/results/<case>.log (server log copy) and itest/results/summary.md.

Usage:
  python -I itest/run_matrix.py                 # all cases
  python -I itest/run_matrix.py paper-1.21.11   # only cases whose id contains one of the args
Env:
  DISKLENS_JAR  path to the plugin jar (default: build/libs/DiskLens-*.jar)
"""
import glob
import json
import os
import re
import shutil
import socket
import struct
import subprocess
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
CACHE = os.path.join(HERE, "cache")
SERVERS = os.path.join(HERE, "servers")
RESULTS = os.path.join(HERE, "results")
UA = "disklens-itest/1.0"

JAVA = {
    8: r"C:\Program Files\Java\jre1.8.0_351\bin\java.exe",
    16: r"C:\Program Files\Java\jdk-16.0.2\bin\java.exe",
    17: r"C:\Program Files\Java\jdk-17.0.12\bin\java.exe",
    21: r"C:\Program Files\Zulu\zulu-21\bin\java.exe",
    23: r"C:\Program Files\Java\jdk-23\bin\java.exe",
}
PLUGMAN = r"P:\TestRustforFolia\plugins\PlugManX-3.0.2.jar"  # user's own copy, copied read-only
LOCAL_FOLIA = r"P:\FoliaRust\Folia\folia-server\build\libs\folia-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar"
LOCAL_MOJANG_CACHE = r"P:\TestRustforFolia\cache\mojang_1.21.11.jar"

# id, core, version, java
CASES = [
    ("paper-1.16.5", "paper", "1.16.5", 8),
    ("paper-1.17.1", "paper", "1.17.1", 17),
    ("paper-1.18.2", "paper", "1.18.2", 17),
    ("paper-1.19.4", "paper", "1.19.4", 17),
    ("paper-1.20.6", "paper", "1.20.6", 21),
    ("paper-1.21.4", "paper", "1.21.4", 21),
    ("paper-1.21.11", "paper", "1.21.11", 21),
    ("purpur-1.16.5", "purpur", "1.16.5", 8),
    ("purpur-1.20.4", "purpur", "1.20.4", 17),
    ("purpur-1.21.11", "purpur", "1.21.11", 21),
    ("folia-1.19.4", "folia", "1.19.4", 17),
    ("folia-1.20.6", "folia", "1.20.6", 21),
    ("folia-1.21.11", "folia", "1.21.11", 21),
    ("folia-local-1.21.11", "folia-local", "1.21.11", 21),
    ("spigot-1.16.5", "spigot", "1.16.5", 16),
    ("spigot-1.21.11", "spigot", "1.21.11", 21),
]


# ---------------------------------------------------------------- downloads

def http_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def download(url, dest):
    tmp = dest + ".part"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=300) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    os.replace(tmp, dest)


def server_jar(core, version):
    os.makedirs(CACHE, exist_ok=True)
    if core == "folia-local":
        return LOCAL_FOLIA if os.path.exists(LOCAL_FOLIA) else None
    if core == "spigot":
        p = os.path.join(HERE, "buildtools", "out", "spigot-%s.jar" % version)
        return p if os.path.exists(p) else None
    dest = os.path.join(CACHE, "%s-%s.jar" % (core, version))
    if os.path.exists(dest):
        return dest
    if core in ("paper", "folia"):
        b = http_json("https://fill.papermc.io/v3/projects/%s/versions/%s/builds/latest" % (core, version))
        url = b["downloads"]["server:default"]["url"]
    elif core == "purpur":
        url = "https://api.purpurmc.org/v2/purpur/%s/latest/download" % version
    else:
        return None
    print("  downloading", url)
    download(url, dest)
    return dest


# ---------------------------------------------------------------- rcon

class Rcon:
    def __init__(self, port, password):
        self.s = socket.create_connection(("127.0.0.1", port), timeout=20)
        self._send(1, 3, password)
        self._recv()

    def _send(self, rid, typ, body):
        b = body.encode("utf-8") + b"\x00\x00"
        self.s.sendall(struct.pack("<iii", len(b) + 8, rid, typ) + b)

    def _recv(self):
        hdr = b""
        while len(hdr) < 4:
            hdr += self.s.recv(4 - len(hdr))
        n = struct.unpack("<i", hdr)[0]
        d = b""
        while len(d) < n:
            d += self.s.recv(n - len(d))
        return d[8:-2].decode("utf-8", "replace")

    def cmd(self, c):
        self._send(2, 2, c)
        return self._recv()

    def close(self):
        self.s.close()


# ---------------------------------------------------------------- server

OLD_CONFIG = """# my own comment, must survive the update
config-version: 1
cache-minutes: 7   # user value
"""


class Server:
    def __init__(self, case_id, core, version, java, index):
        self.id = case_id
        self.core = core
        self.version = version
        self.java = java
        self.dir = os.path.join(SERVERS, case_id)
        self.port = 25601 + index
        self.rcon_port = 25701 + index
        self.proc = None
        self.log_path = os.path.join(self.dir, "logs", "latest.log")

    def prepare(self, jar, plugin_jar, with_plugman):
        os.makedirs(self.dir, exist_ok=True)
        # fresh worlds, logs and plugin data every run; keep server jar caches/libraries
        for d in os.listdir(self.dir):
            p = os.path.join(self.dir, d)
            if d.startswith("world") or d in ("logs", "plugins"):
                shutil.rmtree(p, ignore_errors=True)
        os.makedirs(os.path.join(self.dir, "plugins", "DiskLens"), exist_ok=True)
        shutil.copy(jar, os.path.join(self.dir, "server.jar"))
        shutil.copy(plugin_jar, os.path.join(self.dir, "plugins", os.path.basename(plugin_jar)))
        if with_plugman and os.path.exists(PLUGMAN):
            shutil.copy(PLUGMAN, os.path.join(self.dir, "plugins", "PlugManX.jar"))
        if self.core == "folia-local" and os.path.exists(LOCAL_MOJANG_CACHE):
            os.makedirs(os.path.join(self.dir, "cache"), exist_ok=True)
            shutil.copy(LOCAL_MOJANG_CACHE, os.path.join(self.dir, "cache"))
        # an "old" config to exercise the auto-update
        with open(os.path.join(self.dir, "plugins", "DiskLens", "config.yml"), "w", encoding="utf-8") as f:
            f.write(OLD_CONFIG)
        # some data to find
        big = os.path.join(self.dir, "backups")
        os.makedirs(big, exist_ok=True)
        with open(os.path.join(big, "old-backup.zip"), "wb") as f:
            f.truncate(30 * 1024 * 1024)
        with open(os.path.join(self.dir, "eula.txt"), "w") as f:
            f.write("eula=true\n")
        major, minor = [int(x) for x in self.version.split(".")[:2]]
        flat = "flat" if (major, minor) < (1, 19) else "minecraft\\:flat"
        with open(os.path.join(self.dir, "server.properties"), "w") as f:
            f.write("server-port=%d\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n"
                    "level-type=%s\ngenerate-structures=false\nspawn-protection=0\nmax-players=2\n"
                    "enable-rcon=true\nrcon.port=%d\nrcon.password=disklens\nbroadcast-rcon-to-ops=false\n"
                    % (self.port, flat, self.rcon_port))

    def start(self):
        env = dict(os.environ)
        tmp = os.path.join(PROJECT, ".tmp")
        env["TEMP"] = env["TMP"] = tmp
        args = [JAVA[self.java], "-Xms512M", "-Xmx1536M", "-Djava.io.tmpdir=" + tmp,
                "-Dpaper.disableChannelLimit=true", "-DIReallyKnowWhatIAmDoingISwear=true",
                "-Dcom.mojang.eula.agree=true", "-Dfile.encoding=UTF-8"]
        if self.java >= 16:
            args.append("-Djdk.net.unixdomain.tmpdir=" + tmp)
        args += ["-jar", "server.jar", "--nogui"]
        self.out = open(os.path.join(self.dir, "stdout.log"), "wb")
        self.proc = subprocess.Popen(args, cwd=self.dir, stdin=subprocess.PIPE, stdout=self.out,
                                     stderr=subprocess.STDOUT, env=env)

    def log(self):
        try:
            with open(self.log_path, "r", encoding="utf-8", errors="replace") as f:
                return f.read()
        except OSError:
            return ""

    def mark(self):
        return len(self.log())

    def wait_for(self, pattern, since=0, timeout=60):
        rx = re.compile(pattern)
        end = time.time() + timeout
        while time.time() < end:
            text = self.log()[since:]
            m = rx.search(text)
            if m:
                return m
            if self.proc.poll() is not None:
                return None
            time.sleep(0.5)
        return None

    def console(self, line):
        self.proc.stdin.write((line + "\n").encode("utf-8"))
        self.proc.stdin.flush()

    def stop(self, timeout=120):
        if self.proc is None or self.proc.poll() is not None:
            return
        try:
            self.console("stop")
            self.proc.wait(timeout)
        except Exception:
            self.proc.kill()
        self.out.close()


# ---------------------------------------------------------------- checks

class Checks:
    def __init__(self):
        self.items = []

    def ok(self, name, cond, detail=""):
        self.items.append((name, "PASS" if cond else "FAIL", detail))
        print("   %-36s %s %s" % (name, "PASS" if cond else "FAIL", detail if not cond else ""))
        return cond

    def skip(self, name, why):
        self.items.append((name, "SKIP", why))
        print("   %-36s SKIP %s" % (name, why))

    def passed(self):
        return all(s != "FAIL" for _, s, _ in self.items)


def run_case(case, index, plugin_jar):
    case_id, core, version, java = case
    print("== %s (java %d)" % (case_id, java))
    c = Checks()
    try:
        jar = server_jar(core, version)
    except Exception as e:
        c.ok("download server jar", False, str(e))
        return c
    if jar is None:
        c.skip("server jar", "not available (BuildTools not finished?)")
        return c
    srv = Server(case_id, core, version, java, index)
    srv.prepare(jar, plugin_jar, with_plugman=True)
    srv.start()
    try:
        done = srv.wait_for(r"Done \(", timeout=900)
        if not c.ok("server started", done is not None, "no 'Done (' in log"):
            return c
        log = srv.log()
        c.ok("plugin enabled", re.search(r"\[DiskLens\] (Enabling|Включение) DiskLens", log) is not None
             or "Platform:" in log)
        c.ok("platform detected", re.search(r"\[DiskLens\] Platform: .*" + ("Folia" if "folia" in core else ""), log)
             is not None, re.findall(r"Platform: .*", log)[:1])

        cfg_path = os.path.join(srv.dir, "plugins", "DiskLens", "config.yml")
        cfg = open(cfg_path, encoding="utf-8").read()
        c.ok("config auto-update: keys added", "progress-log-seconds" in cfg and "command-aliases" in cfg)
        c.ok("config auto-update: user value kept", "cache-minutes: 7   # user value" in cfg)
        c.ok("config auto-update: comment kept", "# my own comment" in cfg)
        c.ok("config auto-update: version bumped", "config-version: 2" in cfg)
        c.ok("config auto-update: backup", os.path.exists(cfg_path + ".bak"))
        c.ok("lang files installed",
             all(os.path.exists(os.path.join(srv.dir, "plugins", "DiskLens", "lang", l + ".yml")) for l in ("en", "ru")))

        m = srv.mark()
        srv.console("disklens")
        c.ok("console: scan finished", srv.wait_for(r"Scan finished: [\d ]+ files", m, 120) is not None)
        c.ok("console: tree header", srv.wait_for(r"== \./ \(server root\)", m, 20) is not None)
        c.ok("console: ascii tree + backup found", srv.wait_for(r"\+- backups/\s+30\.0 MB", m, 10) is not None)
        c.ok("console: no colour codes", "\u00a7" not in srv.log()[m:])

        m = srv.mark()
        srv.console("du backups/ 1 3")
        c.ok("alias /du works", srv.wait_for(r"== backups/", m, 20) is not None)

        m = srv.mark()
        srv.console("du files 10")
        c.ok("files list", srv.wait_for(r"\d+\.\s+30\.0 MB\s+backups/old-backup\.zip", m, 20) is not None)

        m = srv.mark()
        srv.console("du export")
        ok = srv.wait_for(r"Report written: ", m, 30) is not None
        reports = glob.glob(os.path.join(srv.dir, "plugins", "DiskLens", "reports", "report-*.txt"))
        c.ok("export report", ok and len(reports) == 1)

        m = srv.mark()
        srv.console("du ../..")
        c.ok("path guard", srv.wait_for(r"You can't leave the server folder", m, 20) is not None)

        m = srv.mark()
        srv.console("du status")
        c.ok("status (cache)", srv.wait_for(r"Cached result from", m, 20) is not None)

        # RCON (sync-only sender: answered from the cache within the call)
        try:
            r = Rcon(srv.rcon_port, "disklens")
            t1 = r.cmd("disklens 1 2")
            t2 = r.cmd("du files 2")
            t3 = r.cmd("du ../")
            r.close()
            c.ok("rcon: tree", "== ./ (server root)" in t1, t1[:120])
            c.ok("rcon: plain text", "\u00a7" not in t1 + t2)
            c.ok("rcon: files", "Largest files on the server" in t2, t2[:120])
            c.ok("rcon: path guard", "leave the server folder" in t3, t3[:120])
        except Exception as e:
            c.ok("rcon", False, repr(e))

        # language switch + reload
        with open(cfg_path, encoding="utf-8") as f:
            cfg = f.read()
        with open(cfg_path, "w", encoding="utf-8") as f:
            f.write(cfg.replace("default-language: en", "default-language: ru"))
        m = srv.mark()
        srv.console("disklens reload")
        c.ok("reload", srv.wait_for(r"Конфиг и языки DiskLens перезагружены", m, 20) is not None)
        m = srv.mark()
        srv.console("du 1 1")
        c.ok("russian output", srv.wait_for(r"корень сервера", m, 20) is not None)

        # PlugManX lifecycle
        log = srv.log()
        if re.search(r"\[PlugManX\] (Enabling|Включение)", log) and "Could not load 'plugins" not in log.split("PlugManX")[0]:
            m = srv.mark()
            srv.console("plugman unload DiskLens")
            unloaded = srv.wait_for(r"DiskLens has been unloaded|unloaded", m, 30) is not None
            c.ok("plugman unload", unloaded)
            m = srv.mark()
            srv.console("du")
            c.ok("alias gone after unload",
                 srv.wait_for(r"Unknown (or incomplete )?command|Неизвестная команда", m, 15) is not None)
            m = srv.mark()
            srv.console("plugman load " + os.path.splitext(os.path.basename(plugin_jar))[0])
            c.ok("plugman load", srv.wait_for(r"has been loaded and enabled|Enabling DiskLens", m, 30) is not None)
            time.sleep(2)
            m = srv.mark()
            srv.console("disklens status")
            c.ok("fresh state after load", srv.wait_for(r"Кеш пуст|cache is empty", m, 15) is not None)
            m = srv.mark()
            srv.console("du 1 1")
            c.ok("alias back after load", srv.wait_for(r"Сканирование завершено|Scan finished", m, 60) is not None)
            # unload during a scan
            m = srv.mark()
            srv.console("du -r")
            srv.console("plugman unload DiskLens")
            ok = srv.wait_for(r"unloaded", m, 30) is not None
            c.ok("unload during scan", ok)
            srv.console("plugman load " + os.path.splitext(os.path.basename(plugin_jar))[0])
            srv.wait_for(r"has been loaded and enabled", m, 30)
        else:
            c.skip("plugman lifecycle", "PlugManX did not enable on this server")

        log = srv.log()
        bad = [l for l in log.splitlines()
               if ("ERROR" in l or "SEVERE" in l or "Exception" in l) and ("disklens" in l.lower())]
        c.ok("no DiskLens errors in log", not bad, bad[:3])
    finally:
        srv.stop()
        os.makedirs(RESULTS, exist_ok=True)
        try:
            shutil.copy(srv.log_path, os.path.join(RESULTS, case_id + ".log"))
        except OSError:
            pass
        log = srv.log()
        c.ok("clean shutdown", re.search(r"Disabling DiskLens|Выключение DiskLens", log) is not None)
    return c


def main():
    jars = sorted(glob.glob(os.path.join(PROJECT, "build", "libs", "DiskLens-*.jar")))
    plugin_jar = os.environ.get("DISKLENS_JAR") or (jars[-1] if jars else None)
    if not plugin_jar:
        sys.exit("build the plugin first (gradlew build)")
    filters = sys.argv[1:]
    os.makedirs(RESULTS, exist_ok=True)
    summary = []
    for i, case in enumerate(CASES):
        if filters and not any(f in case[0] for f in filters):
            continue
        t0 = time.time()
        c = run_case(case, i, plugin_jar)
        summary.append((case, c, time.time() - t0))
        with open(os.path.join(RESULTS, case[0] + ".json"), "w", encoding="utf-8") as f:
            json.dump({"case": case[0], "checks": c.items}, f, ensure_ascii=False, indent=1)

    lines = ["# DiskLens integration results", "", "| Case | Java | Result | Failed / skipped | Time |",
             "|---|---|---|---|---|"]
    for case, c, dt in summary:
        status = "PASS" if c.passed() else "FAIL"
        if c.items and all(s == "SKIP" for _, s, _ in c.items):
            status = "SKIP"
        notes = "; ".join("%s: %s" % (n, s) for n, s, _ in c.items if s != "PASS")
        lines.append("| %s | %d | %s | %s | %ds |" % (case[0], case[3], status, notes or "-", dt))
    text = "\n".join(lines) + "\n"
    print()
    print(text)
    # merge with previous results so partial runs keep the other rows
    with open(os.path.join(RESULTS, "summary-" + time.strftime("%Y%m%d-%H%M%S") + ".md"), "w", encoding="utf-8") as f:
        f.write(text)


if __name__ == "__main__":
    main()
