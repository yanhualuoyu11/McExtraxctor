package com.mcextractor.ui;

import com.mcextractor.model.AssetEntry;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Displays an image asset with zoom and fit-to-window.
 */
public class ImagePreviewPanel extends JPanel {
    private BufferedImage originalImage;
    private double zoom = 1.0;
    private final JLabel imageLabel;
    private final JScrollPane scrollPane;
    private final JLabel statusLabel;

    public ImagePreviewPanel() {
        super(new BorderLayout());
        imageLabel = new JLabel("", SwingConstants.CENTER);
        imageLabel.setOpaque(false);
        scrollPane = new JScrollPane(imageLabel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Color.DARK_GRAY);
        add(scrollPane, BorderLayout.CENTER);

        // zoom toolbar
        JToolBar zoomBar = new JToolBar();
        zoomBar.setFloatable(false);
        JButton zoomIn = new JButton("+");
        JButton zoomOut = new JButton("-");
        JButton zoomFit = new JButton("Fit");
        JButton zoom1 = new JButton("1:1");
        statusLabel = new JLabel(" ");
        zoomBar.add(zoomIn);
        zoomBar.add(zoomOut);
        zoomBar.add(zoomFit);
        zoomBar.add(zoom1);
        zoomBar.add(Box.createHorizontalGlue());
        zoomBar.add(statusLabel);
        add(zoomBar, BorderLayout.SOUTH);

        zoomIn.addActionListener(e -> setZoom(zoom * 1.25));
        zoomOut.addActionListener(e -> setZoom(zoom / 1.25));
        zoomFit.addActionListener(e -> fitToWindow());
        zoom1.addActionListener(e -> setZoom(1.0));
    }

    public void showImage(File file) {
        try {
            originalImage = ImageIO.read(new FileInputStream(file));
            if (originalImage != null) {
                fitToWindow();
                statusLabel.setText(originalImage.getWidth() + " × " +
                        originalImage.getHeight() + "  |  " +
                        (int)(zoom * 100) + "%");
            } else {
                imageLabel.setIcon(null);
                imageLabel.setText("不支持的图片格式");
                statusLabel.setText(" ");
            }
        } catch (IOException ex) {
            imageLabel.setIcon(null);
            imageLabel.setText("图片加载失败: " + ex.getMessage());
            statusLabel.setText(" ");
        }
    }

    private void setZoom(double newZoom) {
        zoom = Math.max(0.1, Math.min(newZoom, 10.0));
        applyZoom();
    }

    private void fitToWindow() {
        if (originalImage == null) return;
        Dimension view = scrollPane.getViewport().getExtentSize();
        double sx = (double) view.width / originalImage.getWidth();
        double sy = (double) view.height / originalImage.getHeight();
        zoom = Math.min(sx, sy) * 0.95;
        if (zoom < 0.01) zoom = 1.0;
        applyZoom();
    }

    private void applyZoom() {
        if (originalImage == null) return;
        int w = (int) (originalImage.getWidth() * zoom);
        int h = (int) (originalImage.getHeight() * zoom);
        if (w < 1 || h < 1) return;
        Image scaled = originalImage.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        imageLabel.setIcon(new ImageIcon(scaled));
        statusLabel.setText(originalImage.getWidth() + " × " +
                originalImage.getHeight() + "  |  " + (int)(zoom * 100) + "%");
    }

    public void clear() {
        imageLabel.setIcon(null);
        imageLabel.setText("选择图片以预览");
        statusLabel.setText(" ");
        originalImage = null;
    }
}
