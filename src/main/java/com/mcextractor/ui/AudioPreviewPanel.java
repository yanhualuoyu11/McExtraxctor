package com.mcextractor.ui;

import com.mcextractor.model.AssetEntry;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;

/**
 * Audio player panel with playback controls.
 * Supports OGG via VorbisSPI (on classpath), WAV, and other
 * formats recognized by javax.sound.
 */
public class AudioPreviewPanel extends JPanel {
    private final JLabel infoLabel;
    private final JButton playBtn, pauseBtn, stopBtn;
    private final JSlider seekSlider;
    private final JLabel timeLabel;
    private final Timer timer;

    private Clip clip;            // for short WAV-like clips
    private SourceDataLine line;  // for streamed formats (OGG)
    private Thread playbackThread;
    private volatile boolean playing = false;
    private volatile boolean paused = false;
    private File currentFile;
    private long totalFrames;
    private long currentFrame;
    private float frameRate = 44100;

    public AudioPreviewPanel() {
        super(new BorderLayout());

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoLabel = new JLabel("选择音频文件以预览", SwingConstants.CENTER);
        infoLabel.setFont(infoLabel.getFont().deriveFont(12f));
        infoPanel.add(infoLabel, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.NORTH);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        playBtn = new JButton("▶");
        pauseBtn = new JButton("❚❚");
        stopBtn = new JButton("■");
        playBtn.setToolTipText("播放");
        pauseBtn.setToolTipText("暂停");
        stopBtn.setToolTipText("停止");

        controlPanel.add(playBtn);
        controlPanel.add(pauseBtn);
        controlPanel.add(stopBtn);

        seekSlider = new JSlider(0, 1000, 0);
        seekSlider.setEnabled(false);
        controlPanel.add(seekSlider);

        timeLabel = new JLabel("00:00 / 00:00");
        controlPanel.add(timeLabel);

        add(controlPanel, BorderLayout.CENTER);

        playBtn.addActionListener(e -> play());
        pauseBtn.addActionListener(e -> pauseResume());
        stopBtn.addActionListener(e -> stop());
        seekSlider.addChangeListener(e -> {
            if (seekSlider.getValueIsAdjusting() && (clip != null || line != null)) {
                double frac = seekSlider.getValue() / 1000.0;
                seek((long)(frac * totalFrames));
            }
        });

        // update seek bar periodically
        timer = new Timer(250, e -> updatePosition());
    }

    public void load(File file) {
        stop();
        currentFile = file;
        infoLabel.setText(file.getName());
    }

    private void play() {
        if (currentFile == null || !currentFile.isFile()) return;
        stop();

        // First, try loading as a Clip (works well for short WAV/PCM files)
        try (AudioInputStream probe = AudioSystem.getAudioInputStream(currentFile)) {
            AudioFormat fmt = probe.getFormat();
            totalFrames = probe.getFrameLength();
            frameRate = fmt.getFrameRate() > 0 ? fmt.getFrameRate() : 44100;

            if (totalFrames > 0 && totalFrames < 48000 * 300) { // up to ~5 min
                try {
                    DataLine.Info info = new DataLine.Info(Clip.class, fmt);
                    clip = (Clip) AudioSystem.getLine(info);
                    clip.open(probe);
                    clip.start();
                    playing = true;
                    paused = false;
                    seekSlider.setEnabled(true);
                    timer.start();
                    updateInfo("正在播放: " + currentFile.getName());
                    clip.addLineListener(evt -> {
                        if (evt.getType() == LineEvent.Type.STOP) {
                            SwingUtilities.invokeLater(() -> {
                                playing = false;
                                timer.stop();
                                updateInfo("播放结束");
                            });
                        }
                    });
                    return;
                } catch (Exception ignored) {
                    // Clip opening failed; close probe and fall through
                    while (probe.read() != -1) { /* drain */ }
                }
            }
        } catch (Exception ignored) {
            // probe failed, fall through to streaming
        }

        // Fallback: stream via SourceDataLine (handles OGG, long files, etc.)
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(currentFile);
            AudioFormat srcFmt = ais.getFormat();
            float sr = srcFmt.getSampleRate() > 0 ? srcFmt.getSampleRate() : 44100;
            AudioFormat decodedFmt = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sr, 16,
                    srcFmt.getChannels() > 0 ? srcFmt.getChannels() : 2,
                    (srcFmt.getChannels() > 0 ? srcFmt.getChannels() : 2) * 2,
                    sr, false);

            // convert if needed
            if (!AudioSystem.isConversionSupported(decodedFmt, srcFmt)) {
                decodedFmt = srcFmt; // use as-is
            }
            AudioInputStream decoded = AudioSystem.getAudioInputStream(decodedFmt, ais);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFmt);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(decodedFmt);
            line.start();
            playing = true;
            paused = false;
            frameRate = decodedFmt.getFrameRate();
            totalFrames = Long.MAX_VALUE; // unknown length
            seekSlider.setEnabled(true);
            timer.start();
            updateInfo("正在播放（流式）: " + currentFile.getName());

            // read & write in background
            AudioInputStream finalAis = decoded;
            SourceDataLine finalLine = line;
            playbackThread = new Thread(() -> {
                try {
                    byte[] buf = new byte[4096];
                    int n;
                    while (playing && (n = finalAis.read(buf)) != -1) {
                        while (paused && playing) {
                            try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
                        }
                        if (!playing) break;
                        finalLine.write(buf, 0, n);
                    }
                    finalLine.drain();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        playing = false;
                        timer.stop();
                        if (line != null && line.isOpen()) line.close();
                        updateInfo("播放结束");
                    });
                }
            }, "audio-playback");
            playbackThread.setDaemon(true);
            playbackThread.start();

        } catch (Exception ex) {
            updateInfo("错误: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void pauseResume() {
        if (!playing) { play(); return; }
        paused = !paused;
        if (clip != null) {
            if (paused) {
                currentFrame = clip.getFramePosition();
                clip.stop();
            } else {
                clip.setFramePosition((int)currentFrame);
                clip.start();
            }
        }
        updateInfo(paused ? "已暂停" : "正在播放: " + currentFile.getName());
    }

    private void stop() {
        playing = false;
        paused = false;
        timer.stop();
        if (playbackThread != null) { playbackThread.interrupt(); playbackThread = null; }
        if (clip != null) { clip.stop(); clip.close(); clip = null; }
        if (line != null) { line.stop(); line.close(); line = null; }
        seekSlider.setValue(0);
        seekSlider.setEnabled(false);
        updateInfo("已停止");
    }

    private void seek(long frame) {
        if (clip != null) {
            clip.setFramePosition((int)frame);
            clip.start();
        }
    }

    private void updatePosition() {
        if (clip != null && clip.isOpen()) {
            long pos = clip.getFramePosition();
            long len = clip.getFrameLength();
            if (len > 0) {
                seekSlider.setValue((int)(pos * 1000 / len));
                timeLabel.setText(formatTime(pos) + " / " + formatTime(len));
            }
        } else if (line != null && line.isOpen()) {
            long pos = line.getFramePosition();
            timeLabel.setText(formatTime(pos));
        }
    }

    private void updateInfo(String text) {
        infoLabel.setText(text);
    }

    private String formatTime(long frames) {
        if (frameRate <= 0) frameRate = 44100;
        long secs = (long)(frames / frameRate);
        long min = secs / 60;
        long sec = secs % 60;
        return String.format("%02d:%02d", min, sec);
    }

    public void clear() {
        stop();
        currentFile = null;
        infoLabel.setText("选择音频文件以预览");
        timeLabel.setText("00:00 / 00:00");
    }
}
