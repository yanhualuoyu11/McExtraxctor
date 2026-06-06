package com.mcextractor.ui;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;

/**
 * Audio player panel with playback controls.
 * Supports OGG via VorbisSPI, WAV, and other formats recognized by javax.sound.
 *
 * Fixes applied:
 * - Use AudioSystem.getAudioFileFormat(File) for reliable OGG duration detection.
 * - Always pre-read decoded PCM into memory so totalFrames is always known;
 *   the progress bar and time label are always accurate, and seek is precise.
 * - Removed the fragile multi-step streamFrom that could consume the decoded
 *   stream before the playback thread ever saw it, causing instant-stop.
 */
public class AudioPreviewPanel extends JPanel {
    private final JLabel infoLabel;
    private final JButton playBtn, pauseBtn, stopBtn;
    private final JSlider seekSlider;
    private final JLabel timeLabel;
    private final Timer timer;

    private Clip clip;
    private SourceDataLine line;
    private Thread playbackThread;
    private volatile boolean playing;
    private volatile boolean paused;
    private File currentFile;

    private long totalFrames;
    private long currentFrame;      // logical stream position (base + line offset)
    private float frameRate = 44100;
    private AudioFormat decodedFormat;
    private boolean isStreaming;

    // Pre-read buffer — every playback goes through memory so totalFrames is
    // always known and the decoded stream is consumed exactly once.
    private byte[] audioData;

    // Guard to prevent updatePosition() → seekSlider.setValue() → ChangeListener
    // → seek() → stopPlaybackOnly() → … infinite restart loop.
    private volatile boolean updatingSlider;

    public AudioPreviewPanel() {
        super(new BorderLayout());

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoLabel = new JLabel("选择音频文件以预览", SwingConstants.CENTER);
        infoLabel.setFont(infoLabel.getFont().deriveFont(12f));
        infoPanel.add(infoLabel, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.NORTH);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        playBtn = new JButton("\u25B6");
        pauseBtn = new JButton("\u275A\u275A");
        stopBtn = new JButton("\u25A0");
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
            if (updatingSlider) return; // programmatic update — don't seek
            if (!seekSlider.isEnabled() || totalFrames <= 0) return;
            boolean adjusting = seekSlider.getValueIsAdjusting();
            if (clip != null && adjusting) {
                seekClip((long) (seekSlider.getValue() / 1000.0 * totalFrames));
            } else if (!adjusting) {
                seek((long) (seekSlider.getValue() / 1000.0 * totalFrames));
            }
        });

        timer = new Timer(250, e -> updatePosition());
    }

    // ---- public API ----

    public void load(File file) {
        stop();
        currentFile = file;
        infoLabel.setText(file.getName());
    }

    public void clear() {
        stop();
        currentFile = null;
        infoLabel.setText("选择音频文件以预览");
        timeLabel.setText("00:00 / 00:00");
    }

    // ---- playback controls ----

    private void play() {
        if (currentFile == null || !currentFile.isFile()) return;
        stop();

        // Try Clip first (short WAV / PCM files)
        try (AudioInputStream probe = AudioSystem.getAudioInputStream(currentFile)) {
            AudioFormat fmt = probe.getFormat();
            long frames = probe.getFrameLength();
            if (frames > 0 && frames < 48000 * 300) {
                try {
                    clip = (Clip) AudioSystem.getLine(new DataLine.Info(Clip.class, fmt));
                    clip.open(probe);
                    totalFrames = frames;
                    frameRate = fmt.getFrameRate() > 0 ? fmt.getFrameRate() : 44100;
                    clip.start();
                    playing = true;
                    isStreaming = false;
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
                    while (probe.read() != -1) { /* drain */ }
                }
            }
        } catch (Exception ignored) {
            // fall through to streaming
        }

        // Stream: decode, pre-read into memory, then play.
        streamFromFile(currentFile, 0);
    }

    private void pauseResume() {
        if (!playing) { play(); return; }
        paused = !paused;
        if (clip != null) {
            if (paused) {
                currentFrame = clip.getFramePosition();
                clip.stop();
            } else {
                clip.setFramePosition((int) currentFrame);
                clip.start();
            }
        }
        updateInfo(paused ? "已暂停" : "正在播放: "
                + (currentFile != null ? currentFile.getName() : ""));
    }

    private void stop() {
        playing = false;
        paused = false;
        isStreaming = false;
        currentFrame = 0;
        timer.stop();
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
        if (clip != null) { clip.stop(); clip.close(); clip = null; }
        if (line != null) { line.stop(); line.close(); line = null; }
        audioData = null;
        updatingSlider = true;
        seekSlider.setValue(0);
        updatingSlider = false;
        seekSlider.setEnabled(false);
        updateInfo("已停止");
    }

    private void stopPlaybackOnly() {
        playing = false;
        paused = false;
        if (playbackThread != null) { playbackThread.interrupt(); playbackThread = null; }
        if (clip != null) { clip.stop(); clip.close(); clip = null; }
        if (line != null) { line.stop(); line.close(); line = null; }
        // keep audioData and totalFrames for seek restart
    }

    // ---- seeking ----

    private void seekClip(long frame) {
        if (clip == null || frame < 0 || totalFrames <= 0) return;
        clip.setFramePosition((int) Math.min(frame, totalFrames - 1));
        if (playing && !clip.isRunning()) clip.start();
    }

    private void seek(long frame) {
        if (frame < 0 || totalFrames <= 0) return;
        long target = Math.min(frame, totalFrames - 1);
        if (clip != null) { seekClip(target); return; }
        if (audioData == null) return;
        stopPlaybackOnly();
        playFromMemory(target);
    }

    // ---- core: decode file → memory → play ----

    /**
     * Decode the given file into PCM, pre-read everything into {@link #audioData},
     * then start playback from {@code startFrame}.
     *
     * <p>totalFrames is determined via (in order):
     * <ol>
     *   <li>{@code AudioSystem.getAudioFileFormat(File)} duration property, or</li>
     *   <li>{@code audioData.length / frameSize} as a precise fallback.</li>
     * </ol>
     */
    private void streamFromFile(File file, long startFrame) {
        try {
            // --- determine duration (fast, metadata-only) ---
            totalFrames = Long.MAX_VALUE;
            try {
                AudioFileFormat aff = AudioSystem.getAudioFileFormat(file);
                Object dur = aff.properties().get("duration");
                if (dur instanceof Number) {
                    long us = ((Number) dur).longValue();
                    float sr = aff.getFormat().getSampleRate();
                    if (sr > 0 && us > 0) {
                        totalFrames = (long) (us * sr / 1_000_000.0);
                    }
                }
            } catch (Exception ignored) { /* keep Long.MAX_VALUE */ }

            // --- decode to PCM ---
            AudioInputStream raw = AudioSystem.getAudioInputStream(file);
            AudioFormat srcFmt = raw.getFormat();
            float sr = srcFmt.getSampleRate() > 0 ? srcFmt.getSampleRate() : 44100;
            int ch = srcFmt.getChannels() > 0 ? srcFmt.getChannels() : 2;
            AudioFormat pcmFmt = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, sr, 16, ch, ch * 2, sr, false);
            if (!AudioSystem.isConversionSupported(pcmFmt, srcFmt)) {
                pcmFmt = srcFmt;
            }
            decodedFormat = pcmFmt;
            AudioInputStream decoded = AudioSystem.getAudioInputStream(pcmFmt, raw);

            // --- pre-read ALL decoded PCM into memory ---
            audioData = readAllBytes(decoded);
            decoded.close();

            int frameSize = pcmFmt.getFrameSize();
            if (frameSize <= 0 || audioData.length == 0) {
                updateInfo("错误: 解码后无数据");
                return;
            }

            // --- compute exact totalFrames if still unknown ---
            if (totalFrames == Long.MAX_VALUE) {
                totalFrames = audioData.length / frameSize;
            }

            frameRate = pcmFmt.getFrameRate();

            // --- start playback from the requested offset ---
            playFromMemory(startFrame);

        } catch (Exception ex) {
            updateInfo("错误: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Play the pre-read {@link #audioData} starting at {@code startFrame}.
     */
    private void playFromMemory(long startFrame) {
        try {
            if (audioData == null || audioData.length == 0) {
                updateInfo("无音频数据");
                return;
            }
            int frameSize = decodedFormat.getFrameSize();
            if (frameSize <= 0) {
                updateInfo("无法确定帧大小");
                return;
            }

            long byteOffset = startFrame * frameSize;
            if (byteOffset >= audioData.length) {
                updateInfo("跳转位置超出音频长度");
                return;
            }

            int remaining = audioData.length - (int) byteOffset;
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData, (int) byteOffset, remaining);
            AudioInputStream memStream = new AudioInputStream(bais, decodedFormat,
                    remaining / frameSize);

            currentFrame = startFrame;
            startStreamingPlayback(memStream, decodedFormat,
                    currentFile != null ? currentFile.getName() : "");

        } catch (Exception ex) {
            updateInfo("错误: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Open a SourceDataLine, start it, and launch the background read-write thread.
     */
    private void startStreamingPlayback(AudioInputStream stream, AudioFormat fmt, String fileName) {
        try {
            line = (SourceDataLine) AudioSystem.getLine(
                    new DataLine.Info(SourceDataLine.class, fmt));
            line.open(fmt);
            line.start();

            playing = true;
            paused = false;
            isStreaming = true;
            seekSlider.setEnabled(true);
            timer.start();
            updateInfo("正在播放（流式）: " + fileName);

            AudioInputStream finalStream = stream;
            SourceDataLine finalLine = line;

            playbackThread = new Thread(() -> {
                try {
                    byte[] buf = new byte[4096];
                    int n;
                    while (playing && (n = finalStream.read(buf)) != -1) {
                        while (paused && playing) {
                            try { Thread.sleep(100); }
                            catch (InterruptedException ie) { break; }
                        }
                        if (!playing) break;
                        finalLine.write(buf, 0, n);
                    }
                    finalLine.drain();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    // Capture the line this thread was using.  If the field has
                    // been replaced (seek / restart), this finally is stale and
                    // must not kill the new playback.
                    SourceDataLine myLine = finalLine;
                    SwingUtilities.invokeLater(() -> {
                        if (line != myLine) return; // newer playback running
                        playing = false;
                        isStreaming = false;
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

    // ---- position / time display ----

    private void updatePosition() {
        boolean userDragging = seekSlider.getValueIsAdjusting();

        if (clip != null && clip.isOpen()) {
            long pos = clip.getFramePosition();
            if (totalFrames > 0) {
                if (!userDragging) {
                    updatingSlider = true;
                    seekSlider.setValue(Math.min((int) (pos * 1000 / totalFrames), 1000));
                    updatingSlider = false;
                }
                timeLabel.setText(formatTime(pos) + " / " + formatTime(totalFrames));
            }
        } else if (line != null && line.isOpen() && isStreaming) {
            long pos = line.getFramePosition() + currentFrame;
            if (totalFrames > 0) {
                if (!userDragging) {
                    updatingSlider = true;
                    seekSlider.setValue(Math.min((int) (pos * 1000 / totalFrames), 1000));
                    updatingSlider = false;
                }
                timeLabel.setText(formatTime(pos) + " / " + formatTime(totalFrames));
            } else {
                timeLabel.setText(formatTime(pos) + " / --:--");
            }
        }
    }

    // ---- helpers ----

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }

    private void updateInfo(String text) {
        infoLabel.setText(text);
    }

    private String formatTime(long frames) {
        if (frameRate <= 0) frameRate = 44100;
        long secs = (long) (frames / frameRate);
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }
}
