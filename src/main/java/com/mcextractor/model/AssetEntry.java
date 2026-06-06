package com.mcextractor.model;

/**
 * Represents a single Minecraft asset entry from the index file.
 */
public class AssetEntry {
    private String path;   // e.g. "minecraft/sounds/music/game/creative/creative1.ogg"
    private String hash;   // SHA1 hex string
    private long size;     // bytes

    public AssetEntry() {}

    public AssetEntry(String path, String hash, long size) {
        this.path = path;
        this.hash = hash;
        this.size = size;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    /** Top-level namespace — e.g. "minecraft", "realms" */
    public String getNamespace() {
        if (path == null) return "";
        int idx = path.indexOf('/');
        return idx > 0 ? path.substring(0, idx) : path;
    }

    /** File extension without dot — e.g. "ogg", "png", "json" */
    public String getExtension() {
        if (path == null) return "";
        int dot = path.lastIndexOf('.');
        return dot > 0 && dot < path.length() - 1
                ? path.substring(dot + 1).toLowerCase() : "";
    }

    /** Category: second path segment — e.g. "sounds", "textures", "models" */
    public String getCategory() {
        if (path == null) return "";
        String[] parts = path.split("/");
        return parts.length >= 2 ? parts[1] : parts[0];
    }

    /** Hashed object file path: e.g. "ab/abc123def..." */
    public String getObjectRelativePath() {
        if (hash == null || hash.length() < 2) return hash;
        return hash.substring(0, 2) + "/" + hash;
    }

    /** Formatted human-readable size */
    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    /** Infer type for preview selection */
    public boolean isImage() {
        return switch (getExtension()) {
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> true;
            default -> false;
        };
    }

    public boolean isAudio() {
        return switch (getExtension()) {
            case "ogg", "wav", "mp3", "flac", "aiff", "au" -> true;
            default -> false;
        };
    }

    public boolean isText() {
        return switch (getExtension()) {
            case "json", "txt", "mcmeta", "properties", "lang",
                 "vsh", "fsh", "glsl", "jem", "jpm" -> true;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return path + " (" + getFormattedSize() + ")";
    }
}
