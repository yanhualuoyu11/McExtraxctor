package com.mcextractor.model;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parsed Minecraft asset index.  Builds lookup maps for quick navigation.
 */
public class AssetIndex {
    private final String sourceName;
    private final List<AssetEntry> allEntries;
    private final Map<String, List<AssetEntry>> byNamespace;
    private final Map<String, List<AssetEntry>> byCategory;
    private final Map<String, List<AssetEntry>> byExtension;
    private final TreeNode rootNode;

    public AssetIndex(String sourceName, List<AssetEntry> entries) {
        this.sourceName = sourceName;
        this.allEntries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.byNamespace = new LinkedHashMap<>();
        this.byCategory = new LinkedHashMap<>();
        this.byExtension = new LinkedHashMap<>();

        for (AssetEntry e : entries) {
            byNamespace.computeIfAbsent(e.getNamespace(), k -> new ArrayList<>()).add(e);
            byCategory.computeIfAbsent(e.getCategory(), k -> new ArrayList<>()).add(e);
            byExtension.computeIfAbsent(e.getExtension(), k -> new ArrayList<>()).add(e);
        }
        this.rootNode = buildTree(entries);
    }

    // ---- accessors ----
    public String getSourceName() { return sourceName; }
    public List<AssetEntry> getAllEntries() { return allEntries; }
    public int size() { return allEntries.size(); }
    public TreeNode getRootNode() { return rootNode; }

    public Set<String> getNamespaces() { return byNamespace.keySet(); }
    public Set<String> getCategories() { return byCategory.keySet(); }
    public Set<String> getExtensions() { return byExtension.keySet(); }

    public List<AssetEntry> getByExtension(String ext) {
        return byExtension.getOrDefault(ext, Collections.emptyList());
    }

    // ---- tree builder ----

    private TreeNode buildTree(List<AssetEntry> entries) {
        TreeNode root = new TreeNode("全部资源", null);
        for (AssetEntry e : entries) {
            String[] parts = e.getPath().split("/");
            TreeNode current = root;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLeaf = (i == parts.length - 1);
                current = current.getOrCreateChild(part, isLeaf);
                if (isLeaf) current.entries.add(e);
            }
        }
        root.sortRecursive();
        return root;
    }

    // ---- JSON parsing ----

    /**
     * Parse a Minecraft asset index JSON file.  Expects:
     * <pre>{ "objects": { "path/to/file.ext": { "hash": "...", "size": N }, ... } }</pre>
     */
    public static AssetIndex parse(File jsonFile) throws IOException {
        try (Reader r = new InputStreamReader(
                new FileInputStream(jsonFile), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonObject objects = root.getAsJsonObject("objects");
            List<AssetEntry> entries = new ArrayList<>(objects.size());

            for (Map.Entry<String, JsonElement> kv : objects.entrySet()) {
                String path = kv.getKey();
                JsonObject obj = kv.getValue().getAsJsonObject();
                String hash = obj.get("hash").getAsString();
                long size = obj.get("size").getAsLong();
                entries.add(new AssetEntry(path, hash, size));
            }
            entries.sort(Comparator.comparing(AssetEntry::getPath));
            return new AssetIndex(jsonFile.getName(), entries);
        }
    }

    // ---- inner class ----

    /**
     * A node in the asset path tree.
     */
    public static class TreeNode {
        public final String name;
        public final TreeNode parent;
        public final Map<String, TreeNode> children = new LinkedHashMap<>();
        public final List<AssetEntry> entries = new ArrayList<>(); // only populated on leaf nodes
        public final boolean isLeaf;

        TreeNode(String name, TreeNode parent) {
            this(name, parent, false);
        }

        TreeNode(String name, TreeNode parent, boolean isLeaf) {
            this.name = name;
            this.parent = parent;
            this.isLeaf = isLeaf;
        }

        TreeNode getOrCreateChild(String name, boolean leaf) {
            return children.computeIfAbsent(name, n -> new TreeNode(n, this, leaf));
        }

        /** Sort children: directories before files, then alphabetically */
        void sortRecursive() {
            List<Map.Entry<String, TreeNode>> sorted = new ArrayList<>(children.entrySet());
            sorted.sort((a, b) -> {
                boolean aDir = !a.getValue().isLeaf;
                boolean bDir = !b.getValue().isLeaf;
                if (aDir != bDir) return aDir ? -1 : 1;
                return a.getKey().compareToIgnoreCase(b.getKey());
            });
            children.clear();
            for (var e : sorted) {
                children.put(e.getKey(), e.getValue());
                e.getValue().sortRecursive();
            }
        }

        /** Total number of leaf entries under this node */
        public int totalEntryCount() {
            int cnt = entries.size();
            for (TreeNode child : children.values())
                cnt += child.totalEntryCount();
            return cnt;
        }

        @Override
        public String toString() {
            int cnt = totalEntryCount();
            return cnt > 0 ? name + " (" + cnt + ")" : name;
        }
    }
}
