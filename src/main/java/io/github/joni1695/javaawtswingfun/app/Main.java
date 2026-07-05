package io.github.joni1695.javaawtswingfun.app;

import javax.swing.SwingUtilities;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ParticlePlayground::new);
    }
}
