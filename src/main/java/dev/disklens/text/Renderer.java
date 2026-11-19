package dev.disklens.text;

import dev.disklens.i18n.Lang;
import dev.disklens.scan.Node;
import dev.disklens.scan.ScanResult;
import dev.disklens.text.Line.Tone;
import dev.disklens.util.Format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds all tree / list output as platform-neutral {@link Line}s. Pure: no Bukkit classes. */
public final class Renderer {
    private final Lang.Msg m;
    private final Glyphs g;
    private final boolean rich;
    private final String cmd;

    /**
     * @param rich clickable/hoverable output (players); false = plain text with hints instead of buttons
     * @param cmd  command prefix used in click actions, e.g. "/disklens"
     */
    public Renderer(Lang.Msg m, Glyphs g, boolean rich, String cmd) {
        this.m = m;
        this.g = g;
        this.rich = rich;
        this.cmd = cmd;
    }

    private String h(long bytes) {
        return Format.human(bytes, m.units());
    }

    public String display(String rel) {
        return rel.isEmpty() ? m.get("root-name") : rel + "/";
    }

    private static String arg(String rel) {
        return rel.isEmpty() ? "./" : rel + "/";
    }

    static Tone sizeTone(double share) {
        if (share >= 0.5) return Tone.RED;
        if (share >= 0.2) return Tone.GOLD;
        if (share >= 0.05) return Tone.YELLOW;
        return Tone.GREEN;
    }

    // ------------------------------------------------------------------ tree

    public List<Line> tree(ScanResult r, String rel, Node node, int depth, int top, int maxLines, String diskLine,
                           long now) {
        List<Line> out = new ArrayList<Line>();
        Line head = new Line()
                .add(g.rule, Tone.DARK_GRAY)
                .add(display(rel), Tone.AQUA).bold()
                .add("  " + h(node.size), Tone.RED)
                .add(", " + m.get("counts", "files", Format.num(node.fileCount), "dirs", Format.num(node.dirCount)),
                        Tone.GRAY);
        if (!rel.isEmpty() && rich) {
            int i = rel.lastIndexOf('/');
            String parent = i < 0 ? "./" : rel.substring(0, i) + "/";
            head.add("  ", Tone.GRAY).add(m.get("button-up"), Tone.GREEN)
                    .click(cmd + " " + parent + " " + depth + " " + top, m.get("hover-up"));
        }
        out.add(head);
        if (diskLine != null) out.add(Line.of(diskLine, Tone.DARK_GRAY));
        int[] budget = {Math.max(1, maxLines)};
        if (children(out, node, rel, "", depth, top, depth, top, budget)) {
            out.add(Line.of(g.ellipsis + " " + m.get("truncated", "lines", maxLines, "cmd", cmd + " export " + arg(rel)),
                    Tone.RED));
        }
        out.addAll(footer(r, rel, now));
        return out;
    }

    /** Full, unlimited tree under {@code node} (for the text report). */
    public List<Line> fullTree(Node node, String rel) {
        List<Line> out = new ArrayList<Line>();
        children(out, node, rel, "", Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, new int[]{Integer.MAX_VALUE});
        return out;
    }

    /** @return true if the line budget ran out. */
    private boolean children(List<Line> out, Node node, String rel, String prefix, int depthLeft, int top,
                             int depth, int origTop, int[] budget) {
        List<Node> kids = node.children;
        int shown = Math.min(top, kids.size());
        long restCount = (kids.size() - shown) + node.hiddenFiles;
        long restSize = node.hiddenFilesSize;
        for (int i = shown; i < kids.size(); i++) restSize += kids.get(i).size;
        boolean hasRest = restCount > 0;

        for (int i = 0; i < shown; i++) {
            if (budget[0]-- <= 0) return true;
            Node c = kids.get(i);
            boolean last = i == shown - 1 && !hasRest;
            String childRel = rel.isEmpty() ? c.name : rel + "/" + c.name;
            out.add(entry(prefix + (last ? g.last : g.tee), c, node.size, childRel, depth, origTop));
            if (c.dir && depthLeft > 1 && !c.children.isEmpty()) {
                if (children(out, c, childRel, prefix + (last ? g.blank : g.pipe), depthLeft - 1, top,
                        depth, origTop, budget)) return true;
            }
        }
        if (hasRest) {
            if (budget[0]-- <= 0) return true;
            String pct = node.size > 0 ? ", " + Format.pct(restSize, node.size) : "";
            out.add(new Line().add(prefix + g.last, Tone.DARK_GRAY)
                    .add(g.ellipsis + " " + m.get("more", "count", Format.num(restCount), "size", h(restSize) + pct),
                            Tone.GRAY).italic());
        }
        return false;
    }

    private Line entry(String branch, Node c, long parentSize, String childRel, int depth, int top) {
        double share = parentSize > 0 ? (double) c.size / parentSize : 0;
        Line l = new Line().add(branch, Tone.DARK_GRAY);
        if (c.dir) {
            l.add(c.name + "/", Tone.AQUA);
            if (rich) l.click(cmd + " " + childRel + "/ " + depth + " " + top, m.get("hover-open", "path", childRel + "/"));
        } else {
            l.add(c.name, Tone.WHITE);
        }
        l.add("  " + h(c.size), sizeTone(share));
        if (parentSize > 0) l.add(" (" + Format.pct(c.size, parentSize) + ")", Tone.GRAY);
        if (c.dir) l.add("  " + m.get("files-short", "count", Format.num(c.fileCount)), Tone.DARK_GRAY);
        return l;
    }

    // ------------------------------------------------------------------ footer / lists

    public List<Line> footer(ScanResult r, String rel, long now) {
        long ago = Math.max(0, (now - r.finishedAtMillis) / 1000);
        String agoStr = ago < 60 ? m.get("ago-seconds", "n", ago) : m.get("ago-minutes", "n", ago / 60);
        Line info = Line.of(m.get("footer", "ago", agoStr, "took", Format.seconds(r.durationMillis),
                "errors", r.errors, "links", r.symlinks), Tone.DARK_GRAY);
        List<Line> out = new ArrayList<Line>();
        String p = arg(rel);
        if (!rich) {
            out.add(info);
            out.add(Line.of(m.get("footer-plain", "rescan", cmd + " " + p + " -r", "export", cmd + " export " + p),
                    Tone.GRAY));
            return out;
        }
        info.add("  ", Tone.GRAY)
                .add(m.get("button-rescan"), Tone.GOLD).click(cmd + " " + p + " -r", m.get("hover-rescan"))
                .add(" ", Tone.GRAY)
                .add(m.get("button-export"), Tone.GOLD).click(cmd + " export " + p, m.get("hover-export"));
        out.add(info);
        return out;
    }

    public List<Line> largestFiles(ScanResult r, int limit, int storedLimit, long now) {
        List<Line> out = new ArrayList<Line>();
        List<ScanResult.FileEntry> files = r.largestFiles;
        out.add(Line.of(g.rule + m.get("files-title", "shown", Math.min(limit, files.size()),
                "total", Format.num(r.tree.fileCount), "size", h(r.tree.size)), Tone.GOLD));
        int i = 0;
        for (ScanResult.FileEntry e : files) {
            if (i++ >= limit) break;
            out.add(new Line()
                    .add(String.format(Locale.ROOT, "%3d. ", i), Tone.DARK_GRAY)
                    .add(h(e.size), sizeTone((double) e.size / Math.max(1, r.tree.size)))
                    .add("  " + e.path, Tone.WHITE));
        }
        if (limit > files.size() && files.size() >= storedLimit) {
            out.add(Line.of(m.get("files-limited", "n", files.size()), Tone.GRAY));
        }
        out.addAll(footer(r, "", now));
        return out;
    }
}
