package dev.disklens.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One directory (or file) in the scanned tree. */
public final class Node {
    public static final Comparator<Node> BY_SIZE_DESC = new Comparator<Node>() {
        @Override
        public int compare(Node a, Node b) {
            return Long.compare(b.size, a.size);
        }
    };

    public final String name;
    public final boolean dir;
    public long size;
    public long fileCount;
    public long dirCount;
    /** Sub-directories and the largest files, sorted by size; null for files. */
    public List<Node> children;
    /** Small files that were not kept individually. */
    public long hiddenFiles;
    public long hiddenFilesSize;

    public Node(String name, boolean dir) {
        this.name = name;
        this.dir = dir;
        if (dir) children = new ArrayList<Node>();
    }

    /** Child directory by name (exact match first, then case-insensitive). */
    public Node child(String name) {
        if (children == null) return null;
        for (Node c : children) {
            if (c.dir && c.name.equals(name)) return c;
        }
        for (Node c : children) {
            if (c.dir && c.name.equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    /** Looks up a directory by a '/'-separated path relative to this node; "" is this node. */
    public Node find(String rel) {
        Node n = this;
        if (rel == null || rel.isEmpty()) return n;
        for (String part : rel.split("/")) {
            if (part.isEmpty()) continue;
            n = n.child(part);
            if (n == null) return null;
        }
        return n;
    }
}
