package io.github.joni1695.javaawtswingfun.player;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import java.io.File;
import java.util.List;

import io.github.joni1695.javaawtswingfun.audio.AudioMeta;
import io.github.joni1695.javaawtswingfun.audio.BeatDetector;

public final class BeatPlayer {

    private static final long SKIP_MICROS = 10_000_000;

    private Clip clip;
    private AudioMeta meta;
    private List<Long> beats = List.of();
    private int next;

    public void load(File file) throws Exception {
        stop();

        AudioInputStream raw = AudioSystem.getAudioInputStream(file);
        AudioFormat base = raw.getFormat();
        float rate = base.getSampleRate() == AudioSystem.NOT_SPECIFIED ? 44100f : base.getSampleRate();
        int channels = base.getChannels() < 1 ? 2 : base.getChannels();

        AudioFormat pcm = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, channels * 2, rate, false);
        AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, raw);
        byte[] data = decoded.readAllBytes();
        decoded.close();
        raw.close();

        beats = BeatDetector.detectBeats(data, pcm);

        Clip loaded = AudioSystem.getClip();
        loaded.open(pcm, data, 0, data.length);
        clip = loaded;
        meta = AudioMeta.read(file);
    }

    public void play() {
        if (clip == null) {
            return;
        }
        if (isAtEnd()) {
            clip.setFramePosition(0);
            resync();
        }
        clip.start();
    }

    public void pause() {
        if (clip != null) {
            clip.stop();
        }
    }

    public void togglePlay() {
        if (clip == null) {
            return;
        }
        if (clip.isRunning()) {
            clip.stop();
        } else {
            play();
        }
    }

    public boolean isAtEnd() {
        return clip != null && clip.getFramePosition() >= clip.getFrameLength();
    }

    public void restart() {
        if (clip != null) {
            clip.setFramePosition(0);
            resync();
        }
    }

    public void skip(int direction) {
        if (clip == null) {
            return;
        }
        seekTo(clip.getMicrosecondPosition() + direction * SKIP_MICROS);
    }

    public void seekTo(long micros) {
        if (clip == null) {
            return;
        }
        long clamped = Math.max(0, Math.min(clip.getMicrosecondLength(), micros));
        clip.setMicrosecondPosition(clamped);
        resync();
    }

    public void stop() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
        beats = List.of();
        meta = null;
        next = 0;
    }

    public int drainBeats() {
        if (clip == null || !clip.isRunning()) {
            return 0;
        }
        long pos = clip.getMicrosecondPosition();
        int count = 0;
        while (next < beats.size() && beats.get(next) <= pos) {
            next++;
            count++;
        }
        return count;
    }

    public boolean isLoaded() {
        return clip != null;
    }

    public boolean isPlaying() {
        return clip != null && clip.isRunning();
    }

    public long positionMicros() {
        return clip == null ? 0 : clip.getMicrosecondPosition();
    }

    public long lengthMicros() {
        return clip == null ? 0 : clip.getMicrosecondLength();
    }

    public AudioMeta meta() {
        return meta;
    }

    private void resync() {
        long pos = clip.getMicrosecondPosition();
        int i = 0;
        while (i < beats.size() && beats.get(i) <= pos) {
            i++;
        }
        next = i;
    }
}
