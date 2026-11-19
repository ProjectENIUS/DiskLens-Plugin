package dev.disklens.text;

import java.util.ArrayList;
import java.util.List;

/** One output line made of styled segments; platform-neutral (rendered to chat components or plain text). */
public final class Line {
    public enum Tone { WHITE, GRAY, DARK_GRAY, AQUA, GOLD, YELLOW, GREEN, RED }

    public static final class Segment {
        public final String text;
        public final Tone tone;
        public boolean bold;
        public boolean italic;
        public String runCommand;
        public String copyText;
        public String hover;

        Segment(String text, Tone tone) {
            this.text = text;
            this.tone = tone;
        }
    }

    public final List<Segment> segments = new ArrayList<Segment>();

    public static Line of(String text, Tone tone) {
        return new Line().add(text, tone);
    }

    public Line add(String text, Tone tone) {
        segments.add(new Segment(text, tone));
        return this;
    }

    /** Styles the last added segment. */
    public Line bold() {
        last().bold = true;
        return this;
    }

    public Line italic() {
        last().italic = true;
        return this;
    }

    public Line click(String command, String hover) {
        last().runCommand = command;
        last().hover = hover;
        return this;
    }

    public Line copy(String text, String hover) {
        last().copyText = text;
        last().hover = hover;
        return this;
    }

    private Segment last() {
        return segments.get(segments.size() - 1);
    }

    /** Plain text without any colour or formatting codes. */
    public String plain() {
        StringBuilder sb = new StringBuilder();
        for (Segment s : segments) sb.append(s.text);
        return sb.toString();
    }

    @Override
    public String toString() {
        return plain();
    }
}
