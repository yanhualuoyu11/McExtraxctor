package com.mcextractor.service;

import com.mcextractor.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Central service: loads indexes, resolves objects, and bridges model ↔ UI.
 */
public class AssetService {
    private AssetIndex currentIndex;
    private File objectsDir;

    public void setObjectsDir(File dir) {
        this.objectsDir = dir;
    }

    public File getObjectsDir() {
        return objectsDir;
    }

    public AssetIndex getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Load an index JSON file and set it as the current index.
     */
    public AssetIndex loadIndex(File indexFile) throws IOException {
        currentIndex = AssetIndex.parse(indexFile);
        return currentIndex;
    }

    /**
     * Resolve an entry to its actual file in the objects directory.
     */
    public File resolveFile(AssetEntry entry) {
        if (objectsDir == null || entry.getHash() == null) return null;
        return new File(objectsDir, entry.getObjectRelativePath());
    }

    /**
     * Check whether the underlying object file exists.
     */
    public boolean fileExists(AssetEntry entry) {
        File f = resolveFile(entry);
        return f != null && f.isFile();
    }

    /**
     * Copy an input stream to a destination file, creating parent directories.
     */
    public static void copyToFile(InputStream in, File dest) throws IOException {
        Files.createDirectories(dest.getParentFile().toPath());
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    /**
     * Filter entries whose paths contain the given filter text (case-insensitive).
     */
    public List<AssetEntry> filter(String text) {
        if (currentIndex == null) return Collections.emptyList();
        if (text == null || text.isBlank()) return currentIndex.getAllEntries();
        String lower = text.toLowerCase();
        return currentIndex.getAllEntries().stream()
                .filter(e -> e.getPath().toLowerCase().contains(lower))
                .toList();
    }
}
