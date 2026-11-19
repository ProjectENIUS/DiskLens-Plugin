package dev.disklens.text;

/** Characters used to draw the tree. */
public final class Glyphs {
    public static final Glyphs UNICODE = new Glyphs("├─ ", "└─ ", "│  ", "   ", "…", "━━ ");
    public static final Glyphs ASCII = new Glyphs("+- ", "\\- ", "|  ", "   ", "...", "== ");

    public final String tee;
    public final String last;
    public final String pipe;
    public final String blank;
    public final String ellipsis;
    public final String rule;

    public Glyphs(String tee, String last, String pipe, String blank, String ellipsis, String rule) {
        this.tee = tee;
        this.last = last;
        this.pipe = pipe;
        this.blank = blank;
        this.ellipsis = ellipsis;
        this.rule = rule;
    }
}
