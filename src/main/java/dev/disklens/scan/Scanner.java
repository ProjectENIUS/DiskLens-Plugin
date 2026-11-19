package dev.disklens.scan;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Walks a directory tree without following symbolic links (or Windows junctions).
 * Pure Java: no Bukkit classes, safe to unit-test. Call {@link #scan()} from a background thread only.
 */
public final class Scanner {

    public final AtomicLong scannedFiles = new AtomicLong();
    public final AtomicLong scannedBytes = new AtomicLong();

    private final Path root;
    private final int filesPerFolder;
    private final int largestFilesLimit;
    private final AtomicBoolean cancel;

    public Scanner(Path root, int filesPerFolder, int largestFilesLimit, AtomicBoolean cancel) {
        this.root = root;
        this.filesPerFolder = Math.max(0, filesPerFolder);
        this.largestFilesLimit = Math.max(1, largestFilesLimit);
        this.cancel = cancel;
    }

    public ScanResult scan() throws IOException {
        long start = System.currentTimeMillis();
        Visitor v = new Visitor();
        Files.walkFileTree(root, v); // no FOLLOW_LINKS
        Node tree = v.result != null ? v.result : new Node(".", true);
        List<ScanResult.FileEntry> largest = new ArrayList<ScanResult.FileEntry>(v.largest);
        Collections.sort(largest, new Comparator<ScanResult.FileEntry>() {
            @Override
            public int compare(ScanResult.FileEntry a, ScanResult.FileEntry b) {
                return Long.compare(b.size, a.size);
            }
        });
        long end = System.currentTimeMillis();
        return new ScanResult(root, tree, end, end - start, v.errors, v.symlinks, largest,
                cancel.get());
    }

    private final class Visitor implements FileVisitor<Path> {
        final Deque<Node> dirs = new ArrayDeque<Node>();
        final Deque<List<Node>> files = new ArrayDeque<List<Node>>();
        final PriorityQueue<ScanResult.FileEntry> largest = new PriorityQueue<ScanResult.FileEntry>(
                16, new Comparator<ScanResult.FileEntry>() {
            @Override
            public int compare(ScanResult.FileEntry a, ScanResult.FileEntry b) {
                return Long.compare(a.size, b.size);
            }
        });
        Node result;
        long errors;
        long symlinks;

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (cancel.get()) return FileVisitResult.TERMINATE;
            if (!dirs.isEmpty() && (attrs.isSymbolicLink() || attrs.isOther() || Files.isSymbolicLink(dir))) {
                symlinks++; // e.g. Windows junctions: never descend
                return FileVisitResult.SKIP_SUBTREE;
            }
            Path fn = dir.getFileName();
            dirs.push(new Node(dirs.isEmpty() || fn == null ? "." : fn.toString(), true));
            files.push(new ArrayList<Node>());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (cancel.get()) return FileVisitResult.TERMINATE;
            if (attrs.isSymbolicLink()) {
                symlinks++;
                return FileVisitResult.CONTINUE;
            }
            Node parent = dirs.peek();
            if (parent == null) return FileVisitResult.CONTINUE;
            long size = attrs.size();
            Node f = new Node(file.getFileName().toString(), false);
            f.size = size;
            f.fileCount = 1;
            files.peek().add(f);
            parent.size += size;
            parent.fileCount++;
            scannedFiles.incrementAndGet();
            scannedBytes.addAndGet(size);
            if (largest.size() < largestFilesLimit || largest.peek().size < size) {
                largest.add(new ScanResult.FileEntry(root.relativize(file).toString().replace('\\', '/'), size));
                if (largest.size() > largestFilesLimit) largest.poll();
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            errors++; // access denied, vanished file, ...
            return cancel.get() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            if (exc != null) errors++;
            Node n = dirs.pop();
            List<Node> fl = files.pop();
            Collections.sort(fl, Node.BY_SIZE_DESC);
            for (int i = 0; i < fl.size(); i++) {
                Node f = fl.get(i);
                if (i < filesPerFolder) {
                    n.children.add(f);
                } else {
                    n.hiddenFiles++;
                    n.hiddenFilesSize += f.size;
                }
            }
            Collections.sort(n.children, Node.BY_SIZE_DESC);
            ((ArrayList<Node>) n.children).trimToSize();
            Node parent = dirs.peek();
            if (parent == null) {
                result = n;
            } else {
                parent.children.add(n);
                parent.size += n.size;
                parent.fileCount += n.fileCount;
                parent.dirCount += n.dirCount + 1;
            }
            return cancel.get() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
        }
    }
}
