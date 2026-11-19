package dev.disklens;

import dev.disklens.i18n.Lang;
import dev.disklens.scan.Node;
import dev.disklens.scan.ScanResult;
import dev.disklens.scan.Scanner;
import dev.disklens.text.Glyphs;
import dev.disklens.text.Line;
import dev.disklens.text.Renderer;
import dev.disklens.util.Format;
import dev.disklens.util.PathGuard;
import dev.disklens.util.YamlMerger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTest {

    @TempDir
    Path tmp;

    private static void file(Path p, int bytes) throws IOException {
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[bytes]);
    }

    private static String resource(String name) throws IOException {
        return new String(Files.readAllBytes(Path.of("src/main/resources/" + name)), StandardCharsets.UTF_8);
    }

    private static Map<String, String> flat(String yaml) {
        // tiny flat-YAML reader for tests: key: "value"
        Map<String, String> m = new HashMap<>();
        for (String l : yaml.split("\n")) {
            if (l.startsWith("#") || !l.contains(":") || l.startsWith(" ")) continue;
            String k = l.substring(0, l.indexOf(':')).trim();
            String v = l.substring(l.indexOf(':') + 1).trim();
            if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) v = v.substring(1, v.length() - 1).replace("\\\"", "\"");
            m.put(k, v);
        }
        return m;
    }

    private static Lang lang(String def) throws IOException {
        Map<String, Map<String, String>> by = new HashMap<>();
        by.put("en", flat(resource("lang/en.yml")));
        by.put("ru", flat(resource("lang/ru.yml")));
        return new Lang(by, def);
    }

    private ScanResult scanSample() throws IOException {
        file(tmp.resolve("world/region/r.0.0.mca"), 5000);
        file(tmp.resolve("world/region/r.0.1.mca"), 3000);
        file(tmp.resolve("world/level.dat"), 100);
        for (int i = 0; i < 30; i++) file(tmp.resolve("logs/old-" + i + ".log.gz"), 10 + i);
        file(tmp.resolve("backup.zip"), 7000);
        file(tmp.resolve("server.properties"), 50);
        return new Scanner(tmp, 5, 10, new AtomicBoolean()).scan();
    }

    // ------------------------------------------------------------------ scanner

    @Test
    void scannerSumsSizesAndSorts() throws IOException {
        ScanResult r = scanSample();
        Node root = r.tree;
        long logs = 0;
        for (int i = 0; i < 30; i++) logs += 10 + i;
        assertEquals(5000 + 3000 + 100 + logs + 7000 + 50, root.size);
        assertEquals(35, root.fileCount);
        assertEquals(3, root.dirCount); // world, world/region, logs
        assertEquals("world", root.children.get(0).name);
        assertEquals("backup.zip", root.children.get(1).name);
        assertEquals(8000, root.find("world/region").size);
        assertFalse(r.cancelled);
        assertEquals(0, r.errors);
    }

    @Test
    void scannerCollapsesSmallFilesPerFolder() throws IOException {
        Node logs = scanSample().tree.find("logs");
        assertNotNull(logs);
        assertEquals(5, logs.children.size());   // files-per-folder = 5
        assertEquals(25, logs.hiddenFiles);
        assertEquals("old-29.log.gz", logs.children.get(0).name);
    }

    @Test
    void scannerKeepsLargestFilesGlobally() throws IOException {
        List<ScanResult.FileEntry> top = scanSample().largestFiles;
        assertEquals(10, top.size());
        assertEquals("backup.zip", top.get(0).path);
        assertEquals("world/region/r.0.0.mca", top.get(1).path);
    }

    @Test
    void scannerHonoursCancel() throws IOException {
        file(tmp.resolve("a/b.txt"), 10);
        AtomicBoolean cancel = new AtomicBoolean(true);
        ScanResult r = new Scanner(tmp, 5, 10, cancel).scan();
        assertTrue(r.cancelled);
    }

    @Test
    void scannerSkipsSymlinks() throws IOException {
        file(tmp.resolve("real/data.bin"), 1000);
        Path outside = Files.createTempDirectory("disklens-outside");
        file(outside.resolve("huge.bin"), 100000);
        try {
            Files.createSymbolicLink(tmp.resolve("link"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            return; // no symlink privilege on this machine (Windows without developer mode)
        }
        ScanResult r = new Scanner(tmp, 5, 10, new AtomicBoolean()).scan();
        assertEquals(1000, r.tree.size);
        assertTrue(r.symlinks >= 1);
    }

    // ------------------------------------------------------------------ path guard

    @Test
    void pathGuardStaysInsideRoot() {
        PathGuard g = new PathGuard(tmp);
        assertEquals("", g.resolve("").rel);
        assertEquals("", g.resolve("./").rel);
        assertEquals("plugins/x", g.resolve("plugins/x/").rel);
        assertEquals("plugins", g.resolve("plugins/x/..").rel);
        assertEquals("world", g.resolve("\\world\\").rel);
        assertTrue(g.resolve("..").outside);
        assertTrue(g.resolve("../..").outside);
        assertTrue(g.resolve("plugins/../../etc").outside);
        assertNull(g.resolve("../").rel);
    }

    // ------------------------------------------------------------------ rendering

    @Test
    void treeRendersTopNAndCollapsesRest() throws IOException {
        ScanResult r = scanSample();
        Renderer rd = new Renderer(lang("en").forDefault(), Glyphs.ASCII, false, "disklens");
        List<Line> lines = rd.tree(r, "", r.tree, 1, 2, 100, null, r.finishedAtMillis);
        List<String> plain = new ArrayList<>();
        for (Line l : lines) plain.add(l.plain());
        assertTrue(plain.get(0).startsWith("== ./ (server root)"), plain.get(0));
        assertTrue(plain.get(1).startsWith("+- world/"), plain.get(1));
        assertTrue(plain.get(2).startsWith("+- backup.zip"), plain.get(2));
        assertTrue(plain.get(3).startsWith("\\- ... 2 more ("), plain.get(3)); // logs + server.properties
        assertTrue(plain.get(4).startsWith("Scan: 0 s ago"), plain.get(4));
        assertTrue(plain.get(5).contains("disklens ./ -r"), plain.get(5));
        for (Line l : lines) {
            for (Line.Segment s : l.segments) {
                assertNull(s.runCommand, "plain output must not carry click actions");
                assertFalse(s.text.contains("§"), "no legacy colour codes in plain text");
            }
        }
    }

    @Test
    void richTreeHasClickableFolders() throws IOException {
        ScanResult r = scanSample();
        Renderer rd = new Renderer(lang("en").forDefault(), Glyphs.UNICODE, true, "/disklens");
        List<Line> lines = rd.tree(r, "world", r.tree.find("world"), 2, 10, 100, null, r.finishedAtMillis);
        boolean regionClick = false;
        boolean up = false;
        for (Line l : lines) {
            for (Line.Segment s : l.segments) {
                if ("/disklens world/region/ 2 10".equals(s.runCommand)) regionClick = true;
                if ("/disklens ./ 2 10".equals(s.runCommand)) up = true;
            }
        }
        assertTrue(regionClick, "folder click command");
        assertTrue(up, "up button");
    }

    @Test
    void lineBudgetTruncates() throws IOException {
        ScanResult r = scanSample();
        Renderer rd = new Renderer(lang("en").forDefault(), Glyphs.ASCII, false, "disklens");
        List<Line> lines = rd.tree(r, "", r.tree, 5, 50, 3, null, r.finishedAtMillis);
        boolean truncated = false;
        for (Line l : lines) if (l.plain().contains("output truncated at 3 lines")) truncated = true;
        assertTrue(truncated);
    }

    @Test
    void russianOutput() throws IOException {
        ScanResult r = scanSample();
        Renderer rd = new Renderer(lang("en").forLocale("ru_RU"), Glyphs.ASCII, false, "disklens");
        List<Line> lines = rd.tree(r, "", r.tree, 1, 1, 100, null, r.finishedAtMillis);
        assertTrue(lines.get(0).plain().contains("корень сервера"), lines.get(0).plain());
        assertTrue(lines.get(2).plain().contains("ещё 3"), lines.get(2).plain());
    }

    // ------------------------------------------------------------------ i18n

    @Test
    void langFallbacks() throws IOException {
        Lang l = lang("ru");
        assertEquals("ru", l.forLocale("de_DE").language()); // unknown -> default
        assertEquals("en", l.forLocale("en_us").language());
        assertEquals("Б", l.forLocale("ru_ru").units()[0]);
        assertEquals("missing-key", l.forDefault().get("missing-key"));
        assertEquals("Open x/", l.forLocale("en").get("hover-open", "path", "x/"));
        assertEquals("{unknown} stays", new Lang(new HashMap<>(), "en").forDefault().get("{unknown} stays"));
    }

    @Test
    void langFilesHaveSameKeys() throws IOException {
        Map<String, String> en = flat(resource("lang/en.yml"));
        Map<String, String> ru = flat(resource("lang/ru.yml"));
        assertEquals(en.keySet(), ru.keySet());
    }

    @Test
    void formatting() {
        String[] u = {"B", "KB", "MB", "GB", "TB", "PB"};
        assertEquals("512 B", Format.human(512, u));
        assertEquals("1.5 KB", Format.human(1536, u));
        assertEquals("154 GB", Format.human(154L * 1024 * 1024 * 1024, u));
        assertEquals("1 234 567", Format.num(1234567));
        assertEquals("50.0%", Format.pct(1, 2));
    }

    // ------------------------------------------------------------------ yaml merge

    @Test
    void yamlMergeAddsMissingKeysKeepsUserValues() throws IOException {
        String def = resource("config.yml");
        String user = "# my comment\nconfig-version: 1\ncache-minutes: 30   # mine\nlanguage: ru\n";
        YamlMerger.Result r = YamlMerger.merge(def, user, "config-version");
        assertTrue(r.changed);
        assertTrue(r.text.contains("# my comment"));
        assertTrue(r.text.contains("cache-minutes: 30   # mine"));
        assertTrue(r.text.contains("language: ru"));
        assertFalse(r.text.contains("language: auto"));
        assertTrue(r.text.contains("config-version: 2"));
        assertTrue(r.text.contains("command-aliases: [du]"));
        assertTrue(r.text.contains("# Short command aliases"), "comments of added keys are copied");
        assertTrue(r.addedKeys.contains("progress-log-seconds"));
        assertFalse(r.addedKeys.contains("cache-minutes"));

        YamlMerger.Result again = YamlMerger.merge(def, r.text, "config-version");
        assertFalse(again.changed, "merge is idempotent");
    }

    @Test
    void yamlMergeHandlesNestedAndCrlf() {
        String def = "a: 1\nlist:\n  - x\n  - y\n# about b\nb: 2\n";
        String user = "a: 5\r\n";
        YamlMerger.Result r = YamlMerger.merge(def, user, null);
        assertEquals("a: 5\n\nlist:\n  - x\n  - y\n\n# about b\nb: 2\n", r.text);
    }
}
