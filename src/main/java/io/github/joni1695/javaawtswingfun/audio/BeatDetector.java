package io.github.joni1695.javaawtswingfun.audio;

import javax.sound.sampled.AudioFormat;

import java.util.ArrayList;
import java.util.List;

public final class BeatDetector {

    private static final int WINDOW = 1024;
    private static final int HISTORY = 43;
    private static final long MIN_GAP_MICROS = 150_000;
    private static final double ENERGY_FLOOR = 1e-4;

    public static List<Long> detectBeats(byte[] pcm, AudioFormat format) {
        int channels = Math.max(1, format.getChannels());
        int frameSize = channels * 2;
        double sampleRate = format.getSampleRate();
        int frames = pcm.length / frameSize;
        int windows = frames / WINDOW;
        if (windows <= HISTORY) {
            return List.of();
        }

        double[] energies = new double[windows];
        for (int w = 0; w < windows; w++) {
            int base = w * WINDOW * frameSize;
            double sum = 0;
            for (int i = 0; i < WINDOW; i++) {
                int off = base + i * frameSize;
                double mono = 0;
                for (int c = 0; c < channels; c++) {
                    int lo = pcm[off + c * 2] & 0xFF;
                    int hi = pcm[off + c * 2 + 1];
                    mono += (short) ((hi << 8) | lo);
                }
                mono /= channels * 32768.0;
                sum += mono * mono;
            }
            energies[w] = sum / WINDOW;
        }

        List<Long> beats = new ArrayList<>();
        long lastBeat = -MIN_GAP_MICROS;
        for (int w = HISTORY; w < windows; w++) {
            double avg = 0;
            for (int h = w - HISTORY; h < w; h++) {
                avg += energies[h];
            }
            avg /= HISTORY;

            double variance = 0;
            for (int h = w - HISTORY; h < w; h++) {
                double d = energies[h] - avg;
                variance += d * d;
            }
            variance /= HISTORY;

            double ratio = variance / (avg * avg + 1e-9);
            double threshold = Math.max(1.3, 1.5 - Math.min(0.2, ratio));

            long time = (long) (w * WINDOW / sampleRate * 1_000_000);
            if (energies[w] > ENERGY_FLOOR
                    && energies[w] > threshold * avg
                    && time - lastBeat >= MIN_GAP_MICROS) {
                beats.add(time);
                lastBeat = time;
            }
        }
        return beats;
    }
}
