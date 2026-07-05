package io.github.joni1695.javaawtswingfun.canvas;

import java.awt.Color;
import java.util.Random;

final class Particle {

    private static final double PULSE_RATE = 0.05;

    private double x, y;
    private double vx, vy;

    private final double pulsePhase;
    private final float hueOffset;
    private final Color confettiColor;

    Particle(double width, double height, double speed, Random rng) {
        x = rng.nextDouble() * width;
        y = rng.nextDouble() * height;
        setDirection(rng.nextDouble() * Math.PI * 2, speed);
        this.pulsePhase = rng.nextDouble() * Math.PI * 2;
        this.hueOffset = rng.nextFloat();
        this.confettiColor = Color.getHSBColor(rng.nextFloat(), 0.7f, 1f);
    }

    void setSpeed(double speed) {
        double current = Math.hypot(vx, vy);
        if (current == 0) {
            vx = speed;
            vy = 0;
        } else {
            vx = vx / current * speed;
            vy = vy / current * speed;
        }
    }

    void burstFrom(double cx, double cy, double minSpeed, double maxSpeed, Random rng) {
        x = cx;
        y = cy;
        double lo = Math.min(minSpeed, maxSpeed);
        double hi = Math.max(minSpeed, maxSpeed);
        double speed = lo + rng.nextDouble() * (hi - lo);
        setDirection(rng.nextDouble() * Math.PI * 2, speed);
    }

    void update(double width, double height, EdgeMode mode, Random rng) {
        x += vx;
        y += vy;
        switch (mode) {
            case BOUNCE -> {
                if (x < 0)          { x = 0;      vx = -vx; }
                else if (x > width) { x = width;  vx = -vx; }
                if (y < 0)          { y = 0;      vy = -vy; }
                else if (y > height){ y = height; vy = -vy; }
            }
            case WRAP -> {
                if (x < 0)          x += width;
                else if (x > width) x -= width;
                if (y < 0)          y += height;
                else if (y > height)y -= height;
            }
            case RESPAWN -> {
                if (x < 0 || x > width || y < 0 || y > height) {
                    double speed = Math.hypot(vx, vy);
                    x = rng.nextDouble() * width;
                    y = rng.nextDouble() * height;
                    setDirection(rng.nextDouble() * Math.PI * 2, speed);
                }
            }
        }
    }

    double pulseFactor(long frame, double amount) {
        return 1 + amount * Math.sin(frame * PULSE_RATE + pulsePhase);
    }

    Color color(long frame, ColorMode mode, double cycleSpeed) {
        return switch (mode) {
            case RAINBOW  -> Color.getHSBColor((float) ((frame * cycleSpeed + hueOffset) % 1.0), 0.85f, 1f);
            case CONFETTI -> confettiColor;
            case MONO     -> Color.WHITE;
        };
    }

    double x() { return x; }
    double y() { return y; }

    private void setDirection(double angle, double speed) {
        vx = Math.cos(angle) * speed;
        vy = Math.sin(angle) * speed;
    }
}
