package com.mcextractor.ui;

import com.mcextractor.model.AssetEntry;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Switches between preview sub-panels based on asset type.
 */
public class PreviewPanel extends JPanel {
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    private final ImagePreviewPanel imagePanel = new ImagePreviewPanel();
    private final AudioPreviewPanel audioPanel = new AudioPreviewPanel();
    private final JTextArea textArea = new JTextArea();
    private final JLabel emptyLabel = new JLabel("选择文件以预览", SwingConstants.CENTER);
    private final JLabel warnLabel;

    private static final String CARD_IMAGE = "image";
    private static final String CARD_AUDIO = "audio";
    private static final String CARD_TEXT  = "text";
    private static final String CARD_EMPTY = "empty";
    private static final String CARD_WARN  = "warning";

    public PreviewPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("预览"));

        emptyLabel.setForeground(Color.GRAY);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(14f));

        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane textScroll = new JScrollPane(textArea);

        warnLabel = new JLabel("", SwingConstants.CENTER);
        warnLabel.setForeground(new Color(180, 80, 30));
        warnLabel.setFont(warnLabel.getFont().deriveFont(13f));

        cardPanel.add(emptyLabel, CARD_EMPTY);
        cardPanel.add(warnLabel, CARD_WARN);
        cardPanel.add(imagePanel, CARD_IMAGE);
        cardPanel.add(audioPanel, CARD_AUDIO);
        cardPanel.add(textScroll, CARD_TEXT);

        add(cardPanel, BorderLayout.CENTER);
        cards.show(cardPanel, CARD_EMPTY);
    }

    /** Show a warning/instruction message in the preview area. */
    public void showWarning(String msg) {
        warnLabel.setText(msg);
        cards.show(cardPanel, CARD_WARN);
    }

    public void preview(File file, AssetEntry entry) {
        if (file == null || entry == null || !file.isFile()) {
            clear();
            return;
        }

        if (entry.isImage()) {
            imagePanel.showImage(file);
            cards.show(cardPanel, CARD_IMAGE);
        } else if (entry.isAudio()) {
            audioPanel.load(file);
            cards.show(cardPanel, CARD_AUDIO);
        } else if (entry.isText() || entry.getExtension().equals("txt")
                || entry.getExtension().equals("lang")) {
            loadText(file);
            cards.show(cardPanel, CARD_TEXT);
        } else {
            emptyLabel.setText("不支持 ." + entry.getExtension() + " 格式的预览");
            cards.show(cardPanel, CARD_EMPTY);
        }
    }

    private void loadText(File file) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                int lines = 0;
                while ((line = r.readLine()) != null && lines < 500) {
                    sb.append(line).append('\n');
                    lines++;
                }
                if (lines >= 500) sb.append("\n...（已截断）");
            }
            textArea.setText(sb.toString());
            textArea.setCaretPosition(0);
        } catch (IOException ex) {
            textArea.setText("文本读取失败: " + ex.getMessage());
        }
    }

    public void clear() {
        imagePanel.clear();
        audioPanel.clear();
        textArea.setText("");
        emptyLabel.setText("选择文件以预览");
        cards.show(cardPanel, CARD_EMPTY);
    }
}
