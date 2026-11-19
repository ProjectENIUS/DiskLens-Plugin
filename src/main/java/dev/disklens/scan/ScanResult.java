package dev.disklens.scan;

import java.nio.file.Path;
import java.util.List;

/** Immutable outcome of one scan. */
public final class ScanResult {
    public final Path root;
    public final Node tree;
    public final long finishedAtMillis;
    public final long durationMillis;
    public final long errors;
    public final long symlinks;
    public final List<FileEntry> largestFiles;
    public final boolean cancelled;

    public ScanResult(Path root, Node tree, long finishedAtMillis, long durationMillis, long errors, long symlinks,
                      List<FileEntry> largestFiles, boolean cancelled) {
        this.root = root;
        this.tree = tree;
        this.finishedAtMillis = finishedAtMillis;
        this.durationMillis = durationMillis;
        this.errors = errors;
        this.symlinks = symlinks;
        this.largestFiles = largestFiles;
        this.cancelled = cancelled;
    }

    /** A file with its path relative to the scan root ('/' separators). */
    public static final class FileEntry {
        public final String path;
        public final long size;

        public FileEntry(String path, long size) {
            this.path = path;
            this.size = size;
        }
    }
}
