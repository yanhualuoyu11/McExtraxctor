package com.mcextractor.service;

import com.mcextractor.model.AssetEntry;
import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;

/**
 * Handles exporting asset files from the hashed-objects directory
 * to user-chosen destinations with their original names and paths.
 */
public class ExportService {

    private final AssetService assetService;

    public ExportService(AssetService assetService) {
        this.assetService = assetService;
    }

    // ---- callback interface ----

    /**
     * Callback for batch export progress reporting and cancellation.
     * All methods are called from the worker thread, not the EDT.
     */
    public interface ExportProgressCallback {
        /** Report progress: current index (1-based), total count, current file path. */
        void onProgress(int current, int total, String currentFile);

        /** Return true if the export should be cancelled. */
        boolean isCancelled();
    }

    // ---- single export (no progress needed) ----

    /**
     * Export a single entry.  Shows a file-save dialog to pick destination.
     */
    public boolean exportSingle(AssetEntry entry, JFrame parent) {
        File src = assetService.resolveFile(entry);
        if (src == null || !src.isFile()) {
            showError(parent, "源文件未找到: " + entry.getPath());
            return false;
        }
        String name = new File(entry.getPath()).getName();
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(name));
        chooser.setDialogTitle("导出: " + name);
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION)
            return false;

        File dest = chooser.getSelectedFile();
        return copy(src, dest, parent);
    }

    // ---- batch export with progress ----

    /**
     * Export a list of entries to a target directory, preserving
     * the relative path structure (e.g. minecraft/textures/...).
     *
     * @param callback  optional progress callback; may be null.
     * @return number of successfully exported files
     */
    public int exportBatch(List<AssetEntry> entries, File targetDir,
                           boolean preserveStructure, JFrame parent,
                           ExportProgressCallback callback) {
        if (entries.isEmpty()) {
            showInfo(parent, "未选中任何资源。");
            return 0;
        }
        int ok = 0, fail = 0;
        int total = entries.size();
        for (int i = 0; i < total; i++) {
            // check cancellation
            if (callback != null && callback.isCancelled()) {
                break;
            }

            AssetEntry e = entries.get(i);
            File src = assetService.resolveFile(e);
            if (src == null || !src.isFile()) {
                fail++;
                // still report progress even on failure
                if (callback != null) {
                    callback.onProgress(i + 1, total, e.getPath());
                }
                continue;
            }

            File dest;
            if (preserveStructure) {
                dest = new File(targetDir, e.getPath());
            } else {
                dest = new File(targetDir, new File(e.getPath()).getName());
            }

            if (copy(src, dest, parent)) {
                ok++;
            } else {
                fail++;
            }

            if (callback != null) {
                callback.onProgress(i + 1, total, e.getPath());
            }
        }
        return ok;
    }

    /**
     * Export everything to a directory.
     */
    public int exportAll(File targetDir, boolean preserveStructure,
                         JFrame parent, ExportProgressCallback callback) {
        if (assetService.getCurrentIndex() == null) return 0;
        return exportBatch(assetService.getCurrentIndex().getAllEntries(),
                targetDir, preserveStructure, parent, callback);
    }

    /**
     * Export entries of a given extension type.
     */
    public int exportByExtension(String ext, File targetDir,
                                 boolean preserveStructure, JFrame parent,
                                 ExportProgressCallback callback) {
        if (assetService.getCurrentIndex() == null) return 0;
        List<AssetEntry> filtered = assetService.getCurrentIndex().getByExtension(ext);
        return exportBatch(filtered, targetDir, preserveStructure, parent, callback);
    }

    // ---- internal ----

    private boolean copy(File src, File dest, JFrame parent) {
        try {
            Files.createDirectories(dest.getParentFile().toPath());
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) {
            showError(parent, "复制失败 " + src.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private void showError(JFrame parent, String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(parent, msg, "导出错误",
                        JOptionPane.ERROR_MESSAGE));
    }

    private void showInfo(JFrame parent, String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(parent, msg, "导出",
                        JOptionPane.INFORMATION_MESSAGE));
    }
}
