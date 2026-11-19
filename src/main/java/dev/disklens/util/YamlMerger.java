package dev.disklens.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text-level merge of flat YAML files (top-level keys only): keys missing from the user's file are appended
 * together with their comments from the bundled default. The user's own lines, values and comments are never
 * rewritten, so this works the same on every Bukkit version (old Bukkit drops comments when saving YAML).
 * Optionally a version key is bumped in place.
 */
public final class YamlMerger {
    private static final Pattern TOP_KEY = Pattern.compile("^([A-Za-z0-9_.\\-]+|\"[^\"]+\"|'[^']+')\\s*:(.*)$");

    public static final class Result {
        public final String text;
        public final List<String> addedKeys;
        public final boolean changed;

        Result(String text, List<String> addedKeys, boolean changed) {
            this.text = text;
            this.addedKeys = addedKeys;
            this.changed = changed;
        }
    }

    private YamlMerger() {
    }

    /** Top-level keys in document order, mapped to their block (preceding comments + key line + nested lines). */
    static Map<String, String> blocks(String text) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        List<String> pending = new ArrayList<String>();
        String key = null;
        StringBuilder block = null;
        for (String line : lines) {
            Matcher m = TOP_KEY.matcher(line);
            boolean top = !line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.startsWith("#")
                    && !line.startsWith("-") && m.matches();
            if (top) {
                if (key != null) out.put(key, block.toString());
                key = unquote(m.group(1));
                block = new StringBuilder();
                for (String p : pending) block.append(p).append('\n');
                pending.clear();
                block.append(line).append('\n');
            } else if (key != null && !line.isEmpty() && (Character.isWhitespace(line.charAt(0)) || line.startsWith("-"))) {
                // nested content of the current key (flush blank lines that were between)
                for (String p : pending) block.append(p).append('\n');
                pending.clear();
                block.append(line).append('\n');
            } else {
                pending.add(line); // comment or blank line: belongs to the next key
            }
        }
        if (key != null) out.put(key, block.toString());
        return out;
    }

    static Set<String> topKeys(String text) {
        return new LinkedHashSet<String>(blocks(text).keySet());
    }

    private static String unquote(String k) {
        if (k.length() >= 2 && (k.startsWith("\"") && k.endsWith("\"") || k.startsWith("'") && k.endsWith("'"))) {
            return k.substring(1, k.length() - 1);
        }
        return k;
    }

    /**
     * @param versionKey top-level key whose value is replaced with the default's value (null = none)
     */
    public static Result merge(String defaults, String user, String versionKey) {
        Map<String, String> def = blocks(defaults);
        Set<String> have = topKeys(user);
        StringBuilder sb = new StringBuilder(user.replace("\r\n", "\n"));
        List<String> added = new ArrayList<String>();
        for (Map.Entry<String, String> e : def.entrySet()) {
            if (have.contains(e.getKey())) continue;
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
            String block = e.getValue();
            if (!block.startsWith("\n")) sb.append('\n');
            sb.append(block);
            added.add(e.getKey());
        }
        String text = sb.toString();
        boolean changed = !added.isEmpty();
        if (versionKey != null && def.containsKey(versionKey) && have.contains(versionKey)) {
            String defLine = lineOf(def.get(versionKey), versionKey);
            Pattern p = Pattern.compile("(?m)^" + Pattern.quote(versionKey) + "\\s*:.*$");
            Matcher m = p.matcher(text);
            if (defLine != null && m.find() && !m.group().trim().equals(defLine.trim())) {
                text = text.substring(0, m.start()) + defLine + text.substring(m.end());
                changed = true;
            }
        }
        return new Result(text, added, changed);
    }

    private static String lineOf(String block, String key) {
        for (String l : block.split("\n")) {
            if (l.startsWith(key)) return l;
        }
        return null;
    }
}
