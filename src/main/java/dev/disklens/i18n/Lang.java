package dev.disklens.i18n;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Message catalogue for all loaded languages. Missing keys fall back to the default language, then to English,
 * then to the key itself, so a partial translation never breaks output.
 */
public final class Lang {
    public static final String FALLBACK = "en";

    private final Map<String, Map<String, String>> byLang;
    private final String defaultLang;

    public Lang(Map<String, Map<String, String>> byLang, String defaultLang) {
        this.byLang = byLang;
        this.defaultLang = byLang.containsKey(norm(defaultLang)) ? norm(defaultLang) : FALLBACK;
    }

    public Set<String> languages() {
        return Collections.unmodifiableSet(byLang.keySet());
    }

    public String defaultLanguage() {
        return defaultLang;
    }

    /** "ru_RU" / "ru-ru" / "RU" -> "ru". */
    static String norm(String code) {
        if (code == null) return "";
        String c = code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        int i = c.indexOf('_');
        return i > 0 ? c.substring(0, i) : c;
    }

    /** Messages for a client locale such as "ru_ru"; unknown locales get the default language. */
    public Msg forLocale(String locale) {
        String l = norm(locale);
        return new Msg(byLang.containsKey(l) ? l : defaultLang);
    }

    public Msg forDefault() {
        return new Msg(defaultLang);
    }

    /** View of the catalogue in one language. */
    public final class Msg {
        private final String lang;
        private final String[] units;

        Msg(String lang) {
            this.lang = lang;
            String[] u = raw("units").split(",");
            if (u.length < 6) u = new String[]{"B", "KB", "MB", "GB", "TB", "PB"};
            for (int i = 0; i < u.length; i++) u[i] = u[i].trim();
            this.units = u;
        }

        public String language() {
            return lang;
        }

        public String[] units() {
            return units;
        }

        private String raw(String key) {
            String v = lookup(lang, key);
            if (v == null) v = lookup(defaultLang, key);
            if (v == null) v = lookup(FALLBACK, key);
            return v == null ? key : v;
        }

        /** Message with {placeholders}: get("x", "size", "1 GB", "count", 3). */
        public String get(String key, Object... kv) {
            String s = raw(key);
            if (kv.length == 0 || s.indexOf('{') < 0) return s;
            Map<String, String> vars = new HashMap<String, String>();
            for (int i = 0; i + 1 < kv.length; i += 2) vars.put(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
            StringBuilder sb = new StringBuilder(s.length() + 16);
            int i = 0;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '{') {
                    int end = s.indexOf('}', i);
                    if (end > i) {
                        String name = s.substring(i + 1, end);
                        String val = vars.get(name);
                        if (val != null) {
                            sb.append(val);
                            i = end + 1;
                            continue;
                        }
                    }
                }
                sb.append(c);
                i++;
            }
            return sb.toString();
        }
    }

    private String lookup(String lang, String key) {
        Map<String, String> m = byLang.get(lang);
        return m == null ? null : m.get(key);
    }
}
