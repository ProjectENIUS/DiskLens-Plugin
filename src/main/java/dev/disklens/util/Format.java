package dev.disklens.util;

import java.util.Locale;

/** Size and number formatting. */
public final class Format {
    private Format() {
    }

    /**
     * @param units localized unit names: bytes, KB, MB, GB, TB, PB (at least 6 entries)
     */
    public static String human(long bytes, String[] units) {
        if (bytes < 1024) return bytes + " " + units[0];
        double v = bytes;
        int u = 0;
        do {
            v /= 1024;
            u++;
        } while (v >= 1024 && u < units.length - 1);
        return String.format(Locale.ROOT, v >= 100 ? "%.0f %s" : "%.1f %s", v, units[u]);
    }

    /** 1234567 -> "1 234 567". */
    public static String num(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    /** part/whole as "12.3%" (empty if whole is 0). */
    public static String pct(long part, long whole) {
        if (whole <= 0) return "";
        return String.format(Locale.ROOT, "%.1f%%", part * 100.0 / whole);
    }

    public static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1f", millis / 1000.0);
    }
}
