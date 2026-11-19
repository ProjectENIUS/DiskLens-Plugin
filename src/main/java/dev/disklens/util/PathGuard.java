package dev.disklens.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Resolves user-supplied paths strictly inside the server root. */
public final class PathGuard {
    /** Result of {@link #resolve}: either a relative path or an error kind. */
    public static final class Resolved {
        public final String rel;     // '/'-separated, "" = root; null on error
        public final boolean outside;
        public final boolean invalid;

        private Resolved(String rel, boolean outside, boolean invalid) {
            this.rel = rel;
            this.outside = outside;
            this.invalid = invalid;
        }

        public boolean ok() {
            return rel != null;
        }
    }

    private final Path root;

    public PathGuard(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Resolved resolve(String arg) {
        String a = arg == null ? "" : arg.trim().replace('\\', '/');
        if (a.isEmpty() || a.equals(".") || a.equals("./") || a.equals("/")) return new Resolved("", false, false);
        while (a.startsWith("/")) a = a.substring(1);
        Path p;
        try {
            p = root.resolve(a).normalize();
        } catch (InvalidPathException e) {
            return new Resolved(null, false, true);
        }
        if (!p.startsWith(root)) return new Resolved(null, true, false);
        return new Resolved(root.relativize(p).toString().replace('\\', '/'), false, false);
    }
}
