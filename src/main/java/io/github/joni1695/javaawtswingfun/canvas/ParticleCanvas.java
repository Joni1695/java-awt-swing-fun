package io.github.joni1695.javaawtswingfun.canvas;

import javax.swing.JPanel;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ParticleCanvas extends JPanel {

    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();

    private BufferedImage buffer;
    private int particleW, particleH;
    private long frame;

    private ShapeType shapeType = ShapeType.CIRCLE;
    private ColorMode colorMode = ColorMode.RAINBOW;
    private Color backgroundColour = Color.BLACK;

    private int particleCount = 150;
    private double ballSize = 9;
    private double driftSpeed = 16;
    private double pulseAmount = 0;
    private double colorCycleSpeed = 0;
    private boolean antialias = true;
    private int trailFade = 0;

    private EdgeMode edgeMode = EdgeMode.BOUNCE;
    private int scatterInterval;
    private double burstMinSpeed = 16;
    private double burstMaxSpeed = 16;

    public ParticleCanvas() {
        setOpaque(true);
        setBackground(Color.BLACK);
    }

    public void tick() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        ensureParticles(w, h);
        frame++;

        if (scatterInterval > 0 && frame % scatterInterval == 0) {
            scatter(w, h);
        }

        for (Particle p : particles) {
            p.update(w, h, edgeMode, rng);
        }

        if (trailFade > 0) {
            renderTrails(w, h);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        if (trailFade > 0) {
            if (buffer != null) {
                g2.drawImage(buffer, 0, 0, null);
            }
        } else {
            applyHints(g2);
            drawParticles(g2);
        }
    }

    public void setShapeType(ShapeType shapeType) {
        this.shapeType = shapeType;
    }

    public void setColorMode(ColorMode colorMode) {
        this.colorMode = colorMode;
    }

    public void setBackgroundColour(Color colour) {
        this.backgroundColour = colour;
        setBackground(colour);
        repaint();
    }

    public void setAntialias(boolean on) {
        this.antialias = on;
        repaint();
    }

    public void setBallSize(double size) {
        this.ballSize = size;
        repaint();
    }

    public void setPulseAmount(double amount) {
        this.pulseAmount = amount;
    }

    public void setColorCycleSpeed(double speed) {
        this.colorCycleSpeed = speed;
    }

    public void setTrailFade(int fade) {
        this.trailFade = Math.max(0, fade);
        buffer = null;
    }

    public void setDriftSpeed(double speed) {
        this.driftSpeed = speed;
        for (Particle p : particles) {
            p.setSpeed(speed);
        }
    }

    public void setParticleCount(int count) {
        this.particleCount = Math.max(1, count);
    }

    public void setEdgeMode(EdgeMode mode) {
        this.edgeMode = mode;
    }

    public void setScatterInterval(int frames) {
        this.scatterInterval = Math.max(0, frames);
    }

    public void setBurstMinSpeed(double speed) {
        this.burstMinSpeed = speed;
    }

    public void setBurstMaxSpeed(double speed) {
        this.burstMaxSpeed = speed;
    }

    public void burstAt(int x, int y) {
        for (Particle p : particles) {
            p.burstFrom(x, y, burstMinSpeed, burstMaxSpeed, rng);
        }
        repaint();
    }

    private void renderTrails(int w, int h) {
        if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h) {
            buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D init = buffer.createGraphics();
            init.setColor(backgroundColour);
            init.fillRect(0, 0, w, h);
            init.dispose();
        }
        Graphics2D g = buffer.createGraphics();
        applyHints(g);
        g.setColor(withAlpha(backgroundColour, trailFade));
        g.fillRect(0, 0, w, h);
        drawParticles(g);
        g.dispose();
    }

    private void drawParticles(Graphics2D g) {
        for (Particle p : particles) {
            double r = ballSize * p.pulseFactor(frame, pulseAmount);
            g.setColor(p.color(frame, colorMode, colorCycleSpeed));
            fillShape(g, p.x() - r, p.y() - r, r * 2);
        }
    }

    private void applyHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private void fillShape(Graphics2D g, double x, double y, double size) {
        if (shapeType == ShapeType.CIRCLE) {
            g.fill(new Ellipse2D.Double(x, y, size, size));
        } else {
            g.fill(new Rectangle2D.Double(x, y, size, size));
        }
    }

    private void scatter(int width, int height) {
        int cx = rng.nextInt(width);
        int cy = rng.nextInt(height);
        for (Particle p : particles) {
            p.burstFrom(cx, cy, burstMinSpeed, burstMaxSpeed, rng);
        }
    }

    private void ensureParticles(int width, int height) {
        if (particles.size() == particleCount && particleW == width && particleH == height) {
            return;
        }
        particleW = width;
        particleH = height;
        particles.clear();
        for (int i = 0; i < particleCount; i++) {
            particles.add(new Particle(width, height, driftSpeed, rng));
        }
    }

    private static Color withAlpha(Color c, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
