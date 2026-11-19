package dev.disklens;

import dev.disklens.i18n.Lang;
import dev.disklens.platform.ChatOut;
import dev.disklens.platform.Platform;
import dev.disklens.scan.Node;
import dev.disklens.scan.ScanResult;
import dev.disklens.scan.Scanner;
import dev.disklens.text.Glyphs;
import dev.disklens.text.Line;
import dev.disklens.text.Line.Tone;
import dev.disklens.text.Renderer;
import dev.disklens.util.Format;
import dev.disklens.util.PathGuard;
import dev.disklens.util.YamlMerger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DiskLens: which folders take the disk space. Works on Spigot, Paper, Purpur and Folia, 1.16.5+.
 *
 * <p>Reload-safe (PlugMan): no static state, no listeners, no own threads; tasks are cancelled, a running scan is
 * interrupted and awaited, aliases are removed from the command map in {@link #onDisable()}.
 */
public final class DiskLensPlugin extends JavaPlugin implements TabExecutor {

    static final String PERM = "disklens.use";
    private static final String[] BUNDLED_LANGS = {"en", "ru"};
    private static final int CONFIG_VERSION = 2;

    private enum Kind { PLAYER, CONSOLE, SYNC_ONLY }

    private interface Handler {
        void handle(ScanResult r, CommandSender to) throws Exception;
    }

    private Platform platform;
    private PathGuard guard;
    private Lang lang;
    private final CommandAliases aliases = new CommandAliases();
    private volatile boolean active;

    private final Object lock = new Object();
    private ScanResult cached;              // guarded by lock
    private AtomicBoolean runningCancel;    // guarded by lock; non-null while a scan runs
    private CountDownLatch runningDone;     // guarded by lock
    private final List<Waiter> waiting = new ArrayList<Waiter>(); // guarded by lock
    private final Set<Player> progressViewers = Collections.newSetFromMap(new ConcurrentHashMap<Player, Boolean>());

    private static final class Waiter {
        final CommandSender to;
        final Handler handler;

        Waiter(CommandSender to, Handler handler) {
            this.to = to;
            this.handler = handler;
        }
    }

    // ================================================================== lifecycle

    @Override
    public void onEnable() {
        platform = Platform.Detect.create(this);
        guard = new PathGuard(Paths.get("").toAbsolutePath());
        synchronized (lock) {
            cached = null;
            runningCancel = null;
            runningDone = null;
            waiting.clear();
        }
        progressViewers.clear();
        loadFiles();
        PluginCommand cmd = getCommand("disklens");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        List<String> taken = aliases.register(getConfig().getStringList("command-aliases"), this, PERM, getLogger());
        Lang.Msg m = lang.forDefault();
        for (String a : taken) getLogger().warning(m.get("log-alias-taken", "alias", a));
        active = true;
        getLogger().info(m.get("log-platform", "platform", platform.name()));
        getLogger().info(m.get("log-root", "path", guard.root()));
    }

    @Override
    public void onDisable() {
        active = false;
        CountDownLatch done;
        synchronized (lock) {
            if (runningCancel != null) runningCancel.set(true);
            done = runningDone;
            cached = null;
            waiting.clear();
        }
        progressViewers.clear();
        if (platform != null) {
            try {
                platform.cancelAll();
            } catch (Throwable ignored) {
                // server is shutting down
            }
        }
        if (done != null) {
            // the walk checks the cancel flag on every entry; waiting keeps it from touching our classes after
            // PlugMan closes the class loader
            try {
                done.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            aliases.unregister(getLogger());
        } catch (Throwable t) {
            getLogger().warning("Alias cleanup failed: " + t);
        }
        PluginCommand cmd = getCommand("disklens");
        if (cmd != null) cmd.setTabCompleter(null);
    }

    // ================================================================== config / language files

    private String resourceText(String name) throws IOException {
        InputStream in = getResource(name);
        if (in == null) return null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    /** Creates the file from the jar, or appends keys that are missing from an existing one (with a backup). */
    private void installOrMerge(String resource, Path file, String versionKey) throws IOException {
        String def = resourceText(resource);
        if (def == null) return;
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.write(file, def.getBytes(StandardCharsets.UTF_8));
            return;
        }
        String user = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        if (user.startsWith("﻿")) user = user.substring(1);
        YamlMerger.Result r = YamlMerger.merge(def, user, versionKey);
        if (!r.changed) return;
        Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        Files.write(file, r.text.getBytes(StandardCharsets.UTF_8));
        if (!r.addedKeys.isEmpty()) {
            getLogger().info(file.getFileName() + ": +" + r.addedKeys);
        }
    }

    private static Map<String, String> flat(String yamlText) {
        Map<String, String> out = new HashMap<String, String>();
        if (yamlText == null) return out;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(yamlText);
        } catch (InvalidConfigurationException e) {
            return out;
        }
        for (String k : y.getKeys(false)) {
            Object v = y.get(k);
            if (v != null && !(v instanceof org.bukkit.configuration.ConfigurationSection)) out.put(k, String.valueOf(v));
        }
        return out;
    }

    private void loadFiles() {
        Path data = getDataFolder().toPath().toAbsolutePath().normalize();
        try {
            installOrMerge("config.yml", data.resolve("config.yml"), "config-version");
            for (String code : BUNDLED_LANGS) {
                installOrMerge("lang/" + code + ".yml", data.resolve("lang").resolve(code + ".yml"), null);
            }
        } catch (IOException e) {
            getLogger().warning("Could not update config/lang files: " + e);
        }
        reloadConfig();
        Map<String, Map<String, String>> byLang = new LinkedHashMap<String, Map<String, String>>();
        try {
            for (String code : BUNDLED_LANGS) byLang.put(code, flat(resourceText("lang/" + code + ".yml")));
        } catch (IOException e) {
            getLogger().warning("Could not read bundled languages: " + e);
        }
        File[] files = data.resolve("lang").toFile().listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (!n.toLowerCase(Locale.ROOT).endsWith(".yml")) continue;
                String code = n.substring(0, n.length() - 4).toLowerCase(Locale.ROOT);
                try {
                    Map<String, String> m = byLang.get(code);
                    if (m == null) byLang.put(code, m = new HashMap<String, String>());
                    m.putAll(flat(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    getLogger().warning("Could not read " + f + ": " + e);
                }
            }
        }
        String def = getConfig().getString("default-language", "en");
        if ("auto".equalsIgnoreCase(def)) def = Locale.getDefault().getLanguage();
        lang = new Lang(byLang, def);
    }

    // ================================================================== senders / messages

    private static Kind kindOf(CommandSender s) {
        if (s instanceof Player) return Kind.PLAYER;
        if (s instanceof ConsoleCommandSender) return Kind.CONSOLE;
        return Kind.SYNC_ONLY; // RCON, command blocks, proxied senders
    }

    private Lang.Msg msg(CommandSender s) {
        String mode = getConfig().getString("language", "auto");
        if (s instanceof Player && "auto".equalsIgnoreCase(mode)) {
            String loc;
            try {
                loc = ((Player) s).getLocale();
            } catch (Throwable t) {
                loc = null;
            }
            return lang.forLocale(loc);
        }
        if (!"auto".equalsIgnoreCase(mode) && s instanceof Player) return lang.forLocale(mode);
        return lang.forDefault();
    }

    private Renderer renderer(CommandSender s) {
        boolean rich = s instanceof Player;
        Glyphs g = rich || !getConfig().getBoolean("console-ascii-tree", true) ? Glyphs.UNICODE : Glyphs.ASCII;
        return new Renderer(msg(s), g, rich, "/disklens");
    }

    /** Immediate reply on the command thread. */
    private void reply(CommandSender s, String text, Tone tone) {
        ChatOut.send(s, Line.of(text, tone));
    }

    /** Delivery from a background thread. */
    private void deliver(final CommandSender to, final List<Line> lines) {
        if (!active) return;
        if (to instanceof Player) {
            final Player p = (Player) to;
            platform.forPlayer(p, new Runnable() {
                @Override
                public void run() {
                    if (p.isOnline()) ChatOut.send(p, lines);
                }
            });
        } else {
            ChatOut.send(to, lines); // console: thread-safe; sync-only senders only reach here synchronously
        }
    }

    private String diskLine(Lang.Msg m) {
        try {
            FileStore fs = Files.getFileStore(guard.root());
            long total = fs.getTotalSpace();
            long free = fs.getUsableSpace();
            if (total <= 0) return null;
            return m.get("disk-line", "used", Format.human(total - free, m.units()),
                    "total", Format.human(total, m.units()), "free", Format.human(free, m.units()));
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================== command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            reply(sender, msg(sender).get("no-permission"), Tone.RED);
            return true;
        }
        if (!active) return true;
        List<String> tokens = new ArrayList<String>(Arrays.asList(args));
        boolean rescan = false;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            String t = tokens.get(i);
            if (t.equalsIgnoreCase("-r") || t.equalsIgnoreCase("--rescan")) {
                tokens.remove(i);
                rescan = true;
            }
        }
        String sub = tokens.isEmpty() ? "" : tokens.get(0).toLowerCase(Locale.ROOT);
        Lang.Msg m = msg(sender);

        if (sub.equals("help") || sub.equals("?")) {
            help(sender, label, m);
        } else if (sub.equals("reload")) {
            loadFiles();
            reply(sender, msg(sender).get("config-reloaded"), Tone.GREEN);
        } else if (sub.equals("status")) {
            status(sender, m);
        } else if (sub.equals("export")) {
            tokens.remove(0);
            final String rel = resolve(sender, join(tokens), m);
            if (rel == null) return true;
            withScan(sender, rescan, true, new Handler() {
                @Override
                public void handle(ScanResult r, CommandSender to) throws Exception {
                    export(to, r, rel);
                }
            });
        } else if (sub.equals("files")) {
            int n = 20;
            if (tokens.size() > 1) n = parseInt(tokens.get(1), 20);
            final int limit = Math.max(1, Math.min(n, 1000));
            withScan(sender, rescan, false, new Handler() {
                @Override
                public void handle(ScanResult r, CommandSender to) {
                    deliver(to, renderer(to).largestFiles(r, limit, getConfig().getInt("largest-files", 100),
                            System.currentTimeMillis()));
                }
            });
        } else {
            List<String> pathParts = new ArrayList<String>();
            List<Integer> numbers = new ArrayList<Integer>();
            for (String t : tokens) {
                if (t.matches("\\d{1,6}")) numbers.add(Integer.parseInt(t));
                else pathParts.add(t);
            }
            int depth = numbers.size() > 0 ? numbers.get(0) : getConfig().getInt("default-depth", 2);
            int top = numbers.size() > 1 ? numbers.get(1) : getConfig().getInt("default-top", 10);
            final int fDepth = Math.max(1, Math.min(depth, 20));
            final int fTop = Math.max(1, Math.min(top, 200));
            final String rel = resolve(sender, join(pathParts), m);
            if (rel == null) return true;
            withScan(sender, rescan, false, new Handler() {
                @Override
                public void handle(ScanResult r, CommandSender to) {
                    showTree(to, r, rel, fDepth, fTop);
                }
            });
        }
        return true;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(p);
        }
        return sb.toString();
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String resolve(CommandSender sender, String arg, Lang.Msg m) {
        PathGuard.Resolved r = guard.resolve(arg);
        if (r.ok()) return r.rel;
        reply(sender, r.outside ? m.get("outside-root") : m.get("bad-path", "path", arg), Tone.RED);
        return null;
    }

    private void help(CommandSender s, String label, Lang.Msg m) {
        String c = (s instanceof Player ? "/" : "") + label;
        reply(s, m.get("help-title"), Tone.GOLD);
        for (String k : new String[]{"help-tree", "help-files", "help-export", "help-status", "help-reload"}) {
            reply(s, m.get(k, "cmd", c), Tone.YELLOW);
        }
        reply(s, m.get("help-hint"), Tone.GRAY);
    }

    private void status(CommandSender s, Lang.Msg m) {
        ScanResult r;
        boolean scanning;
        synchronized (lock) {
            r = cached;
            scanning = runningCancel != null;
        }
        String text = scanning ? m.get("status-scanning") : m.get("status-idle");
        if (r != null) {
            long ago = (System.currentTimeMillis() - r.finishedAtMillis) / 1000;
            text += " " + m.get("status-cached", "ago", m.get("ago-seconds", "n", ago),
                    "size", Format.human(r.tree.size, m.units()), "files", Format.num(r.tree.fileCount));
        } else {
            text += " " + m.get("status-empty");
        }
        reply(s, text, Tone.GRAY);
    }

    // ================================================================== scanning / cache

    private ScanResult freshCache() {
        synchronized (lock) {
            long maxAge = TimeUnit.MINUTES.toMillis(Math.max(0, getConfig().getInt("cache-minutes", 5)));
            if (cached != null && System.currentTimeMillis() - cached.finishedAtMillis <= maxAge) return cached;
            return null;
        }
    }

    private void runHandler(Handler h, ScanResult r, CommandSender to) {
        try {
            h.handle(r, to);
        } catch (Exception e) {
            getLogger().warning("DiskLens: " + e);
            deliver(to, Collections.singletonList(Line.of(msg(to).get("error", "error", e.getMessage()), Tone.RED)));
        }
    }

    /**
     * @param fileIo the handler writes a file: sync-only senders get it done in the background with the path logged
     */
    private void withScan(final CommandSender sender, boolean force, boolean fileIo, final Handler handler) {
        Kind kind = kindOf(sender);
        Lang.Msg m = msg(sender);
        ScanResult fresh = force ? null : freshCache();
        if (kind == Kind.SYNC_ONLY) {
            // RCON / command blocks only see what we send during this call
            if (fresh != null && !fileIo) {
                runHandler(handler, fresh, sender);
                return;
            }
            final CommandSender console = Bukkit.getConsoleSender();
            if (fresh != null) {
                reply(sender, m.get("sync-export"), Tone.GRAY);
                final ScanResult r = fresh;
                platform.async(new Runnable() {
                    @Override
                    public void run() {
                        runHandler(handler, r, console);
                    }
                });
                return;
            }
            startOrJoin(console, handler, true);
            reply(sender, m.get("sync-scan-started"), Tone.GRAY);
            return;
        }
        if (fresh != null) {
            final ScanResult r = fresh;
            platform.async(new Runnable() {
                @Override
                public void run() {
                    runHandler(handler, r, sender);
                }
            });
            return;
        }
        startOrJoin(sender, handler, false);
    }

    private void startOrJoin(CommandSender to, Handler handler, boolean quiet) {
        Lang.Msg m = msg(to);
        synchronized (lock) {
            waiting.add(new Waiter(to, handler));
            if (to instanceof Player) progressViewers.add((Player) to);
            if (runningCancel != null) {
                if (!quiet) reply(to, m.get("scan-already-running"), Tone.GRAY);
                return;
            }
            if (!quiet) reply(to, m.get("scan-started"), Tone.GRAY);
            final AtomicBoolean cancel = new AtomicBoolean();
            final CountDownLatch done = new CountDownLatch(1);
            runningCancel = cancel;
            runningDone = done;
            final Scanner scanner = new Scanner(guard.root(), getConfig().getInt("files-per-folder", 50),
                    getConfig().getInt("largest-files", 100), cancel);
            final Lang.Msg log = lang.forDefault();
            final long logEvery = Math.max(1, getConfig().getInt("progress-log-seconds", 5)) * 1000L;
            final long[] lastLog = {System.currentTimeMillis()};
            getLogger().info(log.get("log-scan-started", "path", guard.root()));
            final Platform.Task progress = platform.asyncRepeating(new Runnable() {
                @Override
                public void run() {
                    if (!active || cancel.get()) return;
                    long files = scanner.scannedFiles.get();
                    long bytes = scanner.scannedBytes.get();
                    for (final Player p : progressViewers) {
                        final String text = "⏳ " + msg(p).get("scan-progress", "files", Format.num(files),
                                "size", Format.human(bytes, msg(p).units()));
                        platform.forPlayer(p, new Runnable() {
                            @Override
                            public void run() {
                                if (p.isOnline()) ChatOut.actionBar(p, text);
                            }
                        });
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastLog[0] >= logEvery) {
                        lastLog[0] = now;
                        getLogger().info(log.get("log-scan-progress", "files", Format.num(files),
                                "size", Format.human(bytes, log.units())));
                    }
                }
            }, 1000);
            platform.async(new Runnable() {
                @Override
                public void run() {
                    ScanResult r = null;
                    Throwable err = null;
                    try {
                        r = scanner.scan();
                    } catch (Throwable t) {
                        err = t;
                    }
                    progress.cancel();
                    List<Waiter> ws;
                    synchronized (lock) {
                        if (r != null && !r.cancelled && active) cached = r;
                        if (runningCancel == cancel) {
                            runningCancel = null;
                            runningDone = null;
                        }
                        ws = new ArrayList<Waiter>(waiting);
                        waiting.clear();
                    }
                    progressViewers.clear();
                    try {
                        if (r != null && r.cancelled) {
                            getLogger().info(log.get("log-scan-cancelled"));
                        } else if (r != null) {
                            getLogger().info(log.get("log-scan-finished", "files", Format.num(r.tree.fileCount),
                                    "size", Format.human(r.tree.size, log.units()), "ms", r.durationMillis,
                                    "errors", r.errors, "links", r.symlinks));
                            if (active) {
                                for (Waiter w : ws) runHandler(w.handler, r, w.to);
                            }
                        } else {
                            getLogger().warning("Scan failed: " + err);
                            for (Waiter w : ws) {
                                deliver(w.to, Collections.singletonList(Line.of(
                                        msg(w.to).get("scan-error", "error", String.valueOf(err)), Tone.RED)));
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                }
            });
        }
    }

    // ================================================================== output

    private void showTree(CommandSender to, ScanResult r, String rel, int depth, int top) {
        Lang.Msg m = msg(to);
        Node node = r.tree.find(rel);
        if (node == null) {
            deliver(to, Collections.singletonList(Line.of(m.get("folder-not-found", "path", rel), Tone.RED)));
            return;
        }
        boolean rich = to instanceof Player;
        int maxLines = Math.max(20, rich ? getConfig().getInt("max-chat-lines", 250)
                : getConfig().getInt("max-console-lines", 1000));
        deliver(to, renderer(to).tree(r, rel, node, depth, top, maxLines, diskLine(m), System.currentTimeMillis()));
    }

    private void export(CommandSender to, ScanResult r, String rel) throws IOException {
        Lang.Msg m = msg(to);
        Node node = r.tree.find(rel);
        if (node == null) {
            deliver(to, Collections.singletonList(Line.of(m.get("folder-not-found", "path", rel), Tone.RED)));
            return;
        }
        Glyphs g = getConfig().getBoolean("report-ascii-tree", false) ? Glyphs.ASCII : Glyphs.UNICODE;
        Renderer rd = new Renderer(m, g, false, "disklens");
        Path dir = getDataFolder().toPath().toAbsolutePath().normalize().resolve("reports");
        Files.createDirectories(dir);
        Date now = new Date();
        Path file = dir.resolve("report-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(now) + ".txt");
        String[] u = m.units();
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8));
        try {
            w.write(m.get("report-title", "time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now)) + "\n");
            w.write(m.get("report-root", "path", r.root) + "\n");
            String disk = diskLine(m);
            if (disk != null) w.write(disk + "\n");
            w.write(m.get("report-folder", "path", rd.display(rel), "size", Format.human(node.size, u),
                    "bytes", node.size, "files", Format.num(node.fileCount), "dirs", Format.num(node.dirCount)) + "\n");
            w.write(m.get("report-scan", "ms", r.durationMillis, "errors", r.errors, "links", r.symlinks) + "\n");
            w.write(m.get("report-note", "n", getConfig().getInt("files-per-folder", 50),
                    "more", g.ellipsis + " " + m.get("more", "count", "N", "size", "…")) + "\n\n");
            for (Line l : rd.fullTree(node, rel)) {
                w.write(l.plain());
                w.write('\n');
            }
            w.write("\n" + m.get("report-largest") + "\n");
            int i = 0;
            for (ScanResult.FileEntry e : r.largestFiles) {
                w.write(String.format(Locale.ROOT, "%4d. %10s  %s%n", ++i, Format.human(e.size, u), e.path));
            }
        } finally {
            w.close();
        }
        String shown = guard.root().relativize(file).toString().replace('\\', '/');
        getLogger().info(lang.forDefault().get("log-report", "path", file));
        if (to instanceof ConsoleCommandSender) return; // the log line is the reply
        Line l = Line.of(m.get("export-done"), Tone.GREEN).add(shown, Tone.WHITE);
        if (to instanceof Player) l.copy(shown, m.get("export-copy-hover"));
        l.add(" (" + Format.human(Files.size(file), u) + ")", Tone.GRAY);
        deliver(to, Collections.singletonList(l));
    }

    // ================================================================== tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> res = new ArrayList<String>();
        if (!active || !sender.hasPermission(PERM) || args.length == 0) return res;
        String cur = args[args.length - 1];
        boolean pathPos = args.length == 1 || (args.length == 2 && args[0].equalsIgnoreCase("export"));
        if (args.length == 1) {
            for (String s : new String[]{"files", "export", "status", "help", "reload", "-r"}) {
                if (s.startsWith(cur.toLowerCase(Locale.ROOT))) res.add(s);
            }
        }
        ScanResult r;
        synchronized (lock) {
            r = cached;
        }
        if (pathPos && r != null) {
            String c = cur.replace('\\', '/');
            int slash = c.lastIndexOf('/');
            String parent = slash < 0 ? "" : c.substring(0, slash);
            String prefix = (slash < 0 ? c : c.substring(slash + 1)).toLowerCase(Locale.ROOT);
            Node n = r.tree.find(parent);
            if (n != null) {
                for (Node k : n.children) {
                    if (k.dir && k.name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        res.add((parent.isEmpty() ? "" : parent + "/") + k.name + "/");
                        if (res.size() > 60) break;
                    }
                }
            }
        }
        return res;
    }
}
