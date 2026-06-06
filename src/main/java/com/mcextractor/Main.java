package com.mcextractor;

import com.mcextractor.ui.MainFrame;

import javax.swing.*;

/**
 * Entry point for McExtractor.
 *
 * Usage:
 *   java -jar McExtractor.jar
 *
 * Place .minecraft/assets/indexes and .minecraft/assets/objects
 * relative to the working directory, or use File > Open Index /
 * Set Objects Directory to point at them.
 */
public class Main {
    public static void main(String[] args) {
        // FlatLaf or system look-and-feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fallback to default
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
