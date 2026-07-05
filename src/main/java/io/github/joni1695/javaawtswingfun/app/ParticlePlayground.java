package io.github.joni1695.javaawtswingfun.app;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;

import io.github.joni1695.javaawtswingfun.audio.AudioMeta;
import io.github.joni1695.javaawtswingfun.canvas.ColorMode;
import io.github.joni1695.javaawtswingfun.canvas.EdgeMode;
import io.github.joni1695.javaawtswingfun.canvas.ParticleCanvas;
import io.github.joni1695.javaawtswingfun.canvas.ShapeType;
import io.github.joni1695.javaawtswingfun.player.BeatPlayer;

public final class ParticlePlayground extends JFrame {

    private static final Color PANEL_BG = new Color(18, 18, 20);
    private static final Color PLAYER_BG = new Color(28, 28, 32);
    private static final Color ART_BG = new Color(45, 45, 52);
    private static final Color ACCENT = new Color(90, 200, 160);
    private static final Color ICON = new Color(179, 179, 179);
    private static final Color ICON_HOVER = Color.WHITE;
    private static final Color SELECT_BG = new Color(48, 54, 52);
    private static final Color PANEL_FG = new Color(230, 230, 235);
    private static final Color PANEL_MUTED = new Color(150, 150, 158);
    private static final int FRAME_DELAY_MS = 15;

    private final ParticleCanvas canvas = new ParticleCanvas();
    private final BeatPlayer player = new BeatPlayer();
    private final Random rng = new Random();

    private enum LoopMode { OFF, ALL, ONE }

    private record Track(File file, AudioMeta meta) { }

    private final List<Track> queue = new ArrayList<>();
    private final Deque<Integer> shuffleBag = new ArrayDeque<>();
    private int currentIndex = -1;
    private LoopMode loopMode = LoopMode.OFF;
    private boolean shuffle;
    private volatile boolean loadingTrack;

    private JLabel artLabel;
    private JLabel titleLabel;
    private JLabel artistLabel;
    private GlyphButton shuffleButton;
    private GlyphButton playPause;
    private GlyphButton loopButton;
    private GlyphButton infoButton;
    private JSlider progress;
    private JLabel curTime;
    private JLabel totalTime;
    private boolean syncingSlider;

    private JPanel sidebar;
    private boolean sidebarOpen;
    private JLabel infoArt;
    private JLabel infoTitle;
    private JLabel infoArtist;
    private JLabel infoAlbum;
    private JLabel infoExtra;
    private JPanel queueList;
    private JPanel lyricsContent;
    private JScrollPane lyricsScroll;
    private CardLayout cardLayout;
    private JPanel cards;
    private final List<JButton> tabButtons = new ArrayList<>();
    private final List<JLabel> syncedLabels = new ArrayList<>();
    private List<AudioMeta.SyncedLine> currentSynced = List.of();
    private int currentSyncedLine = -1;

    public ParticlePlayground() {
        super("Particle Playground");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        canvas.setPreferredSize(new Dimension(760, 540));
        installMouseControls();

        setLayout(new BorderLayout());
        add(canvas, BorderLayout.CENTER);
        add(buildSouth(), BorderLayout.SOUTH);
        add(buildSidebar(), BorderLayout.EAST);

        new Timer(FRAME_DELAY_MS, e -> onTick()).start();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void onTick() {
        canvas.tick();
        int beats = player.drainBeats();
        for (int i = 0; i < beats; i++) {
            fireBeatBurst();
        }
        if (!loadingTrack && player.isLoaded() && !player.isPlaying() && player.isAtEnd()) {
            handleTrackEnd();
        }
        updatePlayerBar();
    }

    private void handleTrackEnd() {
        if (loopMode == LoopMode.ONE) {
            player.play();
            return;
        }
        int next = nextIndex();
        if (next >= 0) {
            playTrack(next, true);
        }
    }

    private int nextIndex() {
        if (queue.isEmpty()) {
            return -1;
        }
        if (shuffle) {
            if (shuffleBag.isEmpty()) {
                if (loopMode == LoopMode.ALL) {
                    refillShuffleBag();
                }
                if (shuffleBag.isEmpty()) {
                    return -1;
                }
            }
            return shuffleBag.poll();
        }
        int n = currentIndex + 1;
        if (n < queue.size()) {
            return n;
        }
        return loopMode == LoopMode.ALL ? 0 : -1;
    }

    private void refillShuffleBag() {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            if (i != currentIndex) {
                idx.add(i);
            }
        }
        Collections.shuffle(idx, rng);
        shuffleBag.clear();
        shuffleBag.addAll(idx);
    }

    private void fireBeatBurst() {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.burstAt(rng.nextInt(w), rng.nextInt(h));
    }

    private void installMouseControls() {
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                canvas.burstAt(e.getX(), e.getY());
            }
        });
    }

    private JComponent buildSouth() {
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(PANEL_BG);
        south.add(buildPlayerBar(), BorderLayout.NORTH);
        south.add(buildControlPanel(), BorderLayout.CENTER);
        return south;
    }

    private JComponent buildPlayerBar() {
        JPanel bar = new JPanel(new BorderLayout(14, 0));
        bar.setBackground(PLAYER_BG);
        bar.setBorder(new EmptyBorder(10, 14, 10, 14));

        bar.add(buildNowPlaying(), BorderLayout.WEST);
        bar.add(buildTransport(), BorderLayout.CENTER);

        JButton load = new JButton("Load Song");
        load.setForeground(Color.BLACK);
        load.setBackground(ACCENT);
        load.setFocusPainted(false);
        load.addActionListener(e -> chooseSong());

        infoButton = new GlyphButton("info", false, "Song info & lyrics");
        infoButton.addActionListener(e -> toggleSidebar());

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        east.setOpaque(false);
        east.add(infoButton);
        east.add(load);
        bar.add(east, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildNowPlaying() {
        artLabel = new JLabel("♪", JLabel.CENTER);
        artLabel.setOpaque(true);
        artLabel.setBackground(ART_BG);
        artLabel.setForeground(PANEL_MUTED);
        artLabel.setPreferredSize(new Dimension(58, 58));
        artLabel.setFont(artLabel.getFont().deriveFont(26f));

        titleLabel = new JLabel("No song loaded");
        titleLabel.setForeground(PANEL_FG);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        artistLabel = new JLabel(" ");
        artistLabel.setForeground(PANEL_MUTED);
        artistLabel.setFont(artistLabel.getFont().deriveFont(11f));
        artistLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Box.createVerticalGlue());
        text.add(titleLabel);
        text.add(Box.createVerticalStrut(3));
        text.add(artistLabel);
        text.add(Box.createVerticalGlue());

        JPanel np = new JPanel(new BorderLayout(10, 0));
        np.setOpaque(false);
        np.setPreferredSize(new Dimension(240, 62));
        np.add(artLabel, BorderLayout.WEST);
        np.add(text, BorderLayout.CENTER);
        return np;
    }

    private JComponent buildTransport() {
        shuffleButton = new GlyphButton("shuffle", false, "Shuffle: off");
        shuffleButton.addActionListener(e -> toggleShuffle());
        GlyphButton back = new GlyphButton("prev", false, "Back 10 seconds");
        back.addActionListener(e -> player.skip(-1));
        playPause = new GlyphButton("play", true, "Play / Pause");
        playPause.addActionListener(e -> {
            player.togglePlay();
            updatePlayPause();
        });
        GlyphButton fwd = new GlyphButton("fwd", false, "Forward 10 seconds");
        fwd.addActionListener(e -> player.skip(1));

        loopButton = new GlyphButton("loop", false, "Repeat: off");
        loopButton.addActionListener(e -> cycleLoopMode());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttons.add(shuffleButton);
        buttons.add(back);
        buttons.add(playPause);
        buttons.add(fwd);
        buttons.add(loopButton);

        curTime = timeLabel("0:00");
        totalTime = timeLabel("0:00");
        progress = new JSlider(0, 1000, 0);
        progress.setOpaque(false);
        progress.setEnabled(false);
        progress.addChangeListener(e -> onSeekChanged());
        progress.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (progress.isEnabled() && progress.getUI() instanceof BasicSliderUI ui) {
                    progress.setValue(ui.valueForXPosition(e.getX()));
                }
            }
        });

        JPanel seek = new JPanel(new BorderLayout(8, 0));
        seek.setOpaque(false);
        seek.setAlignmentX(Component.CENTER_ALIGNMENT);
        seek.setMaximumSize(new Dimension(420, 28));
        seek.add(curTime, BorderLayout.WEST);
        seek.add(progress, BorderLayout.CENTER);
        seek.add(totalTime, BorderLayout.EAST);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(buttons);
        col.add(Box.createVerticalStrut(4));
        col.add(seek);
        return col;
    }

    private JComponent buildControlPanel() {
        JPanel look = group("Look",
                labeledCombo("Shape", new String[] {"Circles", "Squares"}, 0,
                        i -> canvas.setShapeType(ShapeType.values()[i])),
                labeledCombo("Colour", new String[] {"Rainbow", "Confetti", "Mono"}, 0,
                        i -> canvas.setColorMode(ColorMode.values()[i])),
                intCombo("Ball Size", new int[] {4, 6, 9, 12, 16, 20}, 9, canvas::setBallSize),
                buildBackgroundField(),
                buildAntialiasField());

        JPanel motion = group("Motion",
                intCombo("Count", new int[] {50, 100, 150, 200, 250, 300}, 150, canvas::setParticleCount),
                intCombo("Drift Speed", new int[] {4, 8, 12, 16, 20, 24}, 16, canvas::setDriftSpeed),
                labeledCombo("Edges", new String[] {"Bounce", "Respawn", "Wrap"}, 0,
                        i -> canvas.setEdgeMode(EdgeMode.values()[i])));

        JPanel burst = group("Burst",
                intCombo("Burst Min", new int[] {4, 8, 12, 16, 20, 24}, 16, canvas::setBurstMinSpeed),
                intCombo("Burst Max", new int[] {12, 16, 20, 24, 28, 32}, 12, canvas::setBurstMaxSpeed),
                mappedCombo("Auto-Scatter", new String[] {"Off", "Fast", "Medium", "Slow"},
                        new int[] {0, 25, 50, 100}, canvas::setScatterInterval));

        JPanel effects = group("Effects",
                mappedCombo("Colour Cycling", new String[] {"Off", "Slow", "Medium", "Fast"},
                        new int[] {0, 15, 40, 100}, i -> canvas.setColorCycleSpeed(i / 10000.0)),
                mappedCombo("Trails", new String[] {"Off", "Short", "Medium", "Long"},
                        new int[] {0, 90, 45, 20}, canvas::setTrailFade),
                mappedCombo("Pulse", new String[] {"Off", "Subtle", "Medium", "Strong"},
                        new int[] {0, 12, 25, 40}, i -> canvas.setPulseAmount(i / 100.0)));

        JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
        grid.setBackground(PANEL_BG);
        grid.setBorder(new EmptyBorder(4, 8, 8, 8));
        grid.add(look);
        grid.add(motion);
        grid.add(burst);
        grid.add(effects);
        return grid;
    }

    private JPanel group(String title, JComponent... fields) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        p.setBackground(PANEL_BG);
        TitledBorder border = new TitledBorder(new EtchedBorder(), title);
        border.setTitleColor(ACCENT);
        p.setBorder(border);
        for (JComponent f : fields) {
            p.add(f);
        }
        return p;
    }

    private JComponent field(String label, JComponent control) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel l = new JLabel(label);
        l.setForeground(PANEL_MUTED);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);
        p.add(Box.createVerticalStrut(3));
        p.add(control);
        return p;
    }

    private JComponent buildBackgroundField() {
        JCheckBox red = check("R");
        JCheckBox green = check("G");
        JCheckBox blue = check("B");

        ActionListener update = e -> canvas.setBackgroundColour(new Color(
                red.isSelected() ? 255 : 0,
                green.isSelected() ? 255 : 0,
                blue.isSelected() ? 255 : 0));

        red.addActionListener(update);
        green.addActionListener(update);
        blue.addActionListener(update);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false);
        row.add(red);
        row.add(green);
        row.add(blue);
        return field("Background", row);
    }

    private JComponent buildAntialiasField() {
        JCheckBox smoothing = check("On");
        smoothing.setSelected(true);
        smoothing.addActionListener(e -> canvas.setAntialias(smoothing.isSelected()));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false);
        row.add(smoothing);
        return field("Smoothing", row);
    }

    private void chooseSong() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Audio (mp3, wav, aiff, au)", "mp3", "wav", "aiff", "aif", "au"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        boolean wasEmpty = queue.isEmpty();
        for (File f : chooser.getSelectedFiles()) {
            queue.add(new Track(f, AudioMeta.read(f)));
        }
        if (shuffle) {
            refillShuffleBag();
        }
        rebuildQueue();
        if (wasEmpty && !queue.isEmpty()) {
            playTrack(0, true);
        }
    }

    private void selectTrack(int index) {
        playTrack(index, true);
        if (shuffle) {
            refillShuffleBag();
        }
    }

    private void playTrack(int index, boolean autoplay) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        currentIndex = index;
        loadingTrack = true;
        Track track = queue.get(index);
        rebuildQueue();
        new Thread(() -> {
            try {
                player.load(track.file());
                SwingUtilities.invokeLater(() -> {
                    loadingTrack = false;
                    showNowPlaying();
                    if (autoplay) {
                        player.play();
                    }
                    updatePlayPause();
                });
            } catch (UnsupportedAudioFileException ex) {
                SwingUtilities.invokeLater(() -> {
                    loadingTrack = false;
                    JOptionPane.showMessageDialog(this, songFormatMessage(track.file()),
                            "Beat Sync", JOptionPane.ERROR_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    loadingTrack = false;
                    JOptionPane.showMessageDialog(this, "Couldn't play that file:\n" + ex.getMessage(),
                            "Beat Sync", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "song-loader").start();
    }

    private void removeTrack(int index) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        boolean wasCurrent = index == currentIndex;
        boolean wasPlaying = player.isPlaying();
        queue.remove(index);
        if (queue.isEmpty()) {
            currentIndex = -1;
            player.stop();
            clearNowPlaying();
            rebuildQueue();
            return;
        }
        if (wasCurrent) {
            playTrack(Math.min(index, queue.size() - 1), wasPlaying);
        } else {
            if (index < currentIndex) {
                currentIndex--;
            }
            if (shuffle) {
                refillShuffleBag();
            }
            rebuildQueue();
        }
    }

    private void toggleShuffle() {
        shuffle = !shuffle;
        shuffleButton.setActive(shuffle);
        shuffleButton.setToolTipText(shuffle ? "Shuffle: on" : "Shuffle: off");
        if (shuffle) {
            refillShuffleBag();
        } else {
            shuffleBag.clear();
        }
    }

    private void cycleLoopMode() {
        loopMode = switch (loopMode) {
            case OFF -> LoopMode.ALL;
            case ALL -> LoopMode.ONE;
            case ONE -> LoopMode.OFF;
        };
        loopButton.setKind(loopMode == LoopMode.ONE ? "loopone" : "loop");
        loopButton.setActive(loopMode != LoopMode.OFF);
        loopButton.setToolTipText(switch (loopMode) {
            case OFF -> "Repeat: off";
            case ALL -> "Repeat: all";
            case ONE -> "Repeat: one";
        });
    }

    private void toggleSidebar() {
        sidebarOpen = !sidebarOpen;
        sidebar.setVisible(sidebarOpen);
        infoButton.setActive(sidebarOpen);
        int w = sidebar.getPreferredSize().width;
        setSize(getWidth() + (sidebarOpen ? w : -w), getHeight());
        revalidate();
        repaint();
    }

    private void clearNowPlaying() {
        artLabel.setIcon(null);
        artLabel.setText("♪");
        titleLabel.setText("No song loaded");
        artistLabel.setText(" ");
        progress.setEnabled(false);
        progress.setValue(0);
        curTime.setText("0:00");
        totalTime.setText("0:00");
        updatePlayPause();
        showInfo(null);
        showLyrics(null);
    }

    private void showNowPlaying() {
        AudioMeta m = player.meta();
        if (m == null) {
            return;
        }
        if (m.art != null) {
            Image scaled = m.art.getScaledInstance(58, 58, Image.SCALE_SMOOTH);
            artLabel.setIcon(new ImageIcon(scaled));
            artLabel.setText(null);
        } else {
            artLabel.setIcon(null);
            artLabel.setText("♪");
        }
        titleLabel.setText(m.title);
        artistLabel.setText(blankToSpace(m.artist));
        progress.setEnabled(true);
        showInfo(m);
        showLyrics(m);
    }

    private void updatePlayerBar() {
        if (!player.isLoaded()) {
            return;
        }
        long pos = player.positionMicros();
        long len = player.lengthMicros();
        if (len > 0 && !progress.getValueIsAdjusting()) {
            syncingSlider = true;
            progress.setValue((int) (pos * 1000 / len));
            syncingSlider = false;
        }
        curTime.setText(formatTime(pos));
        totalTime.setText(formatTime(len));
        updatePlayPause();
        updateLyricHighlight(pos / 1000);
    }

    private void updatePlayPause() {
        playPause.setKind(player.isPlaying() ? "pause" : "play");
    }

    private void onSeekChanged() {
        if (syncingSlider || progress.getValueIsAdjusting()) {
            return;
        }
        long len = player.lengthMicros();
        player.seekTo(len * progress.getValue() / 1000);
    }

    private static String formatTime(long micros) {
        long seconds = micros / 1_000_000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static String songFormatMessage(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".mp3")) {
            return "Can't decode this MP3.\n\n"
                    + "MP3 support needs the jars in lib/ on the classpath.\n"
                    + "Launch with ./run.sh (or add lib/*.jar to the run classpath)\n"
                    + "instead of running ParticlePlayground on its own.";
        }
        return "Unsupported audio format: " + file.getName() + "\n\n"
                + "Try an mp3, wav, aiff, or au file.";
    }

    private JLabel timeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(PANEL_MUTED);
        l.setFont(l.getFont().deriveFont(11f));
        return l;
    }

    private JComponent intCombo(String title, int[] values, int defaultValue, IntConsumer action) {
        JComboBox<String> combo = new JComboBox<>();
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            combo.addItem(String.valueOf(values[i]));
            if (values[i] == defaultValue) {
                selected = i;
            }
        }
        combo.setSelectedIndex(selected);
        styleCombo(combo);
        combo.addActionListener(e -> action.accept(values[combo.getSelectedIndex()]));
        return field(title, combo);
    }

    private JComponent mappedCombo(String title, String[] labels, int[] values, IntConsumer action) {
        JComboBox<String> combo = new JComboBox<>(labels);
        styleCombo(combo);
        combo.addActionListener(e -> action.accept(values[combo.getSelectedIndex()]));
        return field(title, combo);
    }

    private JComponent labeledCombo(String title, String[] labels, int defaultIndex, IntConsumer onIndex) {
        JComboBox<String> combo = new JComboBox<>(labels);
        combo.setSelectedIndex(defaultIndex);
        styleCombo(combo);
        combo.addActionListener(e -> onIndex.accept(combo.getSelectedIndex()));
        return field(title, combo);
    }

    private static void styleCombo(JComboBox<?> combo) {
        combo.setForeground(PANEL_FG);
        combo.setBackground(PANEL_BG);
    }

    private static JCheckBox check(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setForeground(PANEL_FG);
        box.setBackground(PANEL_BG);
        box.setOpaque(false);
        return box;
    }

    private JComponent buildSidebar() {
        sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(PANEL_BG);
        sidebar.setPreferredSize(new Dimension(300, 10));
        sidebar.setBorder(new EmptyBorder(10, 12, 12, 12));

        JLabel heading = new JLabel("Now Playing");
        heading.setForeground(PANEL_FG);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
        JButton collapse = flatButton("✕", "Close");
        collapse.addActionListener(e -> toggleSidebar());
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.add(heading, BorderLayout.WEST);
        headerRow.add(collapse, BorderLayout.EAST);

        JPanel tabBar = new JPanel(new GridLayout(1, 3, 6, 0));
        tabBar.setOpaque(false);
        tabBar.setBorder(new EmptyBorder(10, 0, 10, 0));
        tabButtons.clear();
        tabBar.add(makeTab("Queue"));
        tabBar.add(makeTab("Info"));
        tabBar.add(makeTab("Lyrics"));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(headerRow, BorderLayout.NORTH);
        top.add(tabBar, BorderLayout.SOUTH);

        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        cards.setBackground(PANEL_BG);
        cards.add(buildQueueTab(), "Queue");
        cards.add(buildInfoTab(), "Info");
        cards.add(buildLyricsTab(), "Lyrics");

        sidebar.add(top, BorderLayout.NORTH);
        sidebar.add(cards, BorderLayout.CENTER);
        sidebar.setVisible(false);
        showTab("Queue");
        return sidebar;
    }

    private JButton makeTab(String name) {
        JButton b = new JButton(name);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setForeground(Color.WHITE);
        b.setBackground(PANEL_BG);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> showTab(name));
        tabButtons.add(b);
        return b;
    }

    private void showTab(String name) {
        cardLayout.show(cards, name);
        for (JButton b : tabButtons) {
            boolean active = b.getText().equals(name);
            b.setBackground(active ? ACCENT : PANEL_BG);
            b.setForeground(Color.WHITE);
        }
    }

    private JComponent buildQueueTab() {
        queueList = new JPanel();
        queueList.setBackground(PANEL_BG);
        queueList.setLayout(new BoxLayout(queueList, BoxLayout.Y_AXIS));
        JScrollPane sp = new JScrollPane(queueList);
        sp.setBorder(null);
        sp.setBackground(PANEL_BG);
        sp.getViewport().setBackground(PANEL_BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        rebuildQueue();
        return sp;
    }

    private void rebuildQueue() {
        if (queueList == null) {
            return;
        }
        queueList.removeAll();
        if (queue.isEmpty()) {
            JLabel empty = new JLabel("Queue is empty");
            empty.setForeground(PANEL_MUTED);
            empty.setBorder(new EmptyBorder(8, 6, 8, 6));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            queueList.add(empty);
        } else {
            for (int i = 0; i < queue.size(); i++) {
                queueList.add(queueRow(i));
                queueList.add(Box.createVerticalStrut(4));
            }
        }
        queueList.revalidate();
        queueList.repaint();
    }

    private JComponent queueRow(int index) {
        Track track = queue.get(index);
        boolean current = index == currentIndex;

        JLabel thumb = new JLabel();
        thumb.setPreferredSize(new Dimension(38, 38));
        if (track.meta().art != null) {
            thumb.setIcon(new ImageIcon(track.meta().art.getScaledInstance(38, 38, Image.SCALE_SMOOTH)));
        } else {
            thumb.setOpaque(true);
            thumb.setBackground(ART_BG);
            thumb.setHorizontalAlignment(JLabel.CENTER);
            thumb.setForeground(PANEL_MUTED);
            thumb.setText("♪");
        }

        JLabel t = new JLabel(track.meta().title);
        t.setForeground(current ? ACCENT : PANEL_FG);
        t.setFont(t.getFont().deriveFont(current ? Font.BOLD : Font.PLAIN, 12f));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel a = new JLabel(blankToSpace(track.meta().artist));
        a.setForeground(PANEL_MUTED);
        a.setFont(a.getFont().deriveFont(10.5f));
        a.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.add(Box.createVerticalGlue());
        textCol.add(t);
        textCol.add(a);
        textCol.add(Box.createVerticalGlue());

        JButton remove = flatButton("✕", "Remove from queue");
        remove.addActionListener(e -> removeTrack(index));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(current ? SELECT_BG : PANEL_BG);
        row.setBorder(new EmptyBorder(5, 6, 5, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.add(thumb, BorderLayout.WEST);
        row.add(textCol, BorderLayout.CENTER);
        row.add(remove, BorderLayout.EAST);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectTrack(index);
            }
        });
        return row;
    }

    private JComponent buildInfoTab() {
        infoArt = new JLabel();
        infoArt.setAlignmentX(Component.CENTER_ALIGNMENT);
        infoArt.setHorizontalAlignment(JLabel.CENTER);
        infoArt.setMaximumSize(new Dimension(220, 220));

        infoTitle = infoLabel(Font.BOLD, 15f, ACCENT);
        infoArtist = infoLabel(Font.PLAIN, 12f, Color.WHITE);
        infoAlbum = infoLabel(Font.PLAIN, 12f, Color.WHITE);
        infoExtra = infoLabel(Font.PLAIN, 11f, Color.WHITE);

        JPanel p = new JPanel();
        p.setBackground(PANEL_BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(14, 6, 6, 6));
        p.add(infoArt);
        p.add(Box.createVerticalStrut(14));
        p.add(infoTitle);
        p.add(Box.createVerticalStrut(4));
        p.add(infoArtist);
        p.add(Box.createVerticalStrut(2));
        p.add(infoAlbum);
        p.add(Box.createVerticalStrut(10));
        p.add(infoExtra);
        showInfo(null);
        return p;
    }

    private JLabel infoLabel(int style, float size, Color color) {
        JLabel l = new JLabel(" ", JLabel.CENTER);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setForeground(color);
        l.setFont(l.getFont().deriveFont(style, size));
        return l;
    }

    private void showInfo(AudioMeta m) {
        if (infoArt == null) {
            return;
        }
        if (m == null) {
            infoArt.setIcon(null);
            infoTitle.setText("No song loaded");
            infoArtist.setText(" ");
            infoAlbum.setText(" ");
            infoExtra.setText(" ");
            return;
        }
        if (m.art != null) {
            infoArt.setIcon(new ImageIcon(m.art.getScaledInstance(200, 200, Image.SCALE_SMOOTH)));
        } else {
            infoArt.setIcon(null);
        }
        infoTitle.setText(m.title);
        infoArtist.setText(blankToSpace(m.artist));
        infoAlbum.setText(m.album == null || m.album.isBlank() ? " " : "Album: " + m.album);
        long secs = player.lengthMicros() / 1_000_000;
        infoExtra.setText(String.format("Length  %d:%02d", secs / 60, secs % 60));
    }

    private JComponent buildLyricsTab() {
        lyricsContent = new JPanel();
        lyricsContent.setBackground(PANEL_BG);
        lyricsContent.setLayout(new BoxLayout(lyricsContent, BoxLayout.Y_AXIS));
        lyricsContent.setBorder(new EmptyBorder(10, 8, 10, 8));
        lyricsScroll = new JScrollPane(lyricsContent);
        lyricsScroll.setBorder(null);
        lyricsScroll.setBackground(PANEL_BG);
        lyricsScroll.getViewport().setBackground(PANEL_BG);
        lyricsScroll.getVerticalScrollBar().setUnitIncrement(16);
        showLyrics(null);
        return lyricsScroll;
    }

    private void showLyrics(AudioMeta m) {
        if (lyricsContent == null) {
            return;
        }
        lyricsContent.removeAll();
        syncedLabels.clear();
        currentSynced = List.of();
        currentSyncedLine = -1;

        if (m != null && !m.syncedLyrics.isEmpty()) {
            currentSynced = m.syncedLyrics;
            for (AudioMeta.SyncedLine line : currentSynced) {
                JLabel l = new JLabel(line.text());
                l.setForeground(Color.WHITE);
                l.setFont(l.getFont().deriveFont(13f));
                l.setAlignmentX(Component.LEFT_ALIGNMENT);
                l.setBorder(new EmptyBorder(3, 2, 3, 2));
                syncedLabels.add(l);
                lyricsContent.add(l);
            }
        } else if (m != null && m.lyrics != null && !m.lyrics.isBlank()) {
            JTextArea area = new JTextArea(m.lyrics);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(false);
            area.setForeground(Color.WHITE);
            area.setFont(area.getFont().deriveFont(13f));
            area.setAlignmentX(Component.LEFT_ALIGNMENT);
            lyricsContent.add(area);
        } else {
            JLabel none = new JLabel(m == null ? "No song loaded" : "No lyrics for this song");
            none.setForeground(PANEL_MUTED);
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            lyricsContent.add(none);
        }
        lyricsContent.revalidate();
        lyricsContent.repaint();
    }

    private void updateLyricHighlight(long posMs) {
        if (syncedLabels.isEmpty()) {
            return;
        }
        int active = -1;
        for (int i = 0; i < currentSynced.size(); i++) {
            if (currentSynced.get(i).timeMs() <= posMs) {
                active = i;
            } else {
                break;
            }
        }
        if (active == currentSyncedLine) {
            return;
        }
        if (currentSyncedLine >= 0 && currentSyncedLine < syncedLabels.size()) {
            JLabel prev = syncedLabels.get(currentSyncedLine);
            prev.setForeground(Color.WHITE);
            prev.setFont(prev.getFont().deriveFont(Font.PLAIN, 13f));
        }
        currentSyncedLine = active;
        if (active >= 0) {
            JLabel cur = syncedLabels.get(active);
            cur.setForeground(ACCENT);
            cur.setFont(cur.getFont().deriveFont(Font.BOLD, 13f));
            cur.scrollRectToVisible(new java.awt.Rectangle(0, -50, cur.getWidth(), cur.getHeight() + 100));
        }
    }

    private static String blankToSpace(String s) {
        return s == null || s.isBlank() ? " " : s;
    }

    private JButton flatButton(String text, String tip) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setForeground(PANEL_MUTED);
        b.setBackground(PANEL_BG);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static final class GlyphButton extends JButton {

        private String kind;
        private boolean active;
        private final boolean emphasized;

        GlyphButton(String kind, boolean emphasized, String tip) {
            this.kind = kind;
            this.emphasized = emphasized;
            setToolTipText(tip);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            int d = emphasized ? 42 : 32;
            setPreferredSize(new Dimension(d, d));
        }

        void setKind(String kind) {
            if (!this.kind.equals(kind)) {
                this.kind = kind;
                repaint();
            }
        }

        void setActive(boolean active) {
            if (this.active != active) {
                this.active = active;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            boolean hover = getModel().isRollover();

            if (emphasized) {
                double d = Math.min(getWidth(), getHeight()) - 4;
                g2.setColor(hover ? ICON_HOVER : new Color(224, 224, 224));
                g2.fill(new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d));
                g2.setColor(PANEL_BG);
                drawGlyph(g2, kind, cx, cy, 6.0);
            } else {
                g2.setColor(active ? ACCENT : (hover ? ICON_HOVER : ICON));
                drawGlyph(g2, kind, cx, cy, 7.5);
            }
            g2.dispose();
        }

        private static void drawGlyph(Graphics2D g2, String kind, double cx, double cy, double r) {
            switch (kind) {
                case "play" -> g2.fill(triangleRight(cx - r * 0.5, cy, r));
                case "pause" -> {
                    double bw = r * 0.55;
                    double gap = r * 0.45;
                    g2.fill(new Rectangle2D.Double(cx - gap - bw, cy - r, bw, r * 2));
                    g2.fill(new Rectangle2D.Double(cx + gap, cy - r, bw, r * 2));
                }
                case "prev" -> {
                    g2.fill(triangleLeft(cx + r * 1.1, cy, r));
                    g2.fill(triangleLeft(cx - r * 0.1, cy, r));
                }
                case "fwd" -> {
                    g2.fill(triangleRight(cx - r * 1.1, cy, r));
                    g2.fill(triangleRight(cx + r * 0.1, cy, r));
                }
                case "loop" -> drawLoop(g2, cx, cy, r);
                case "loopone" -> {
                    drawLoop(g2, cx, cy, r);
                    drawOne(g2, cx, cy, r);
                }
                case "shuffle" -> drawShuffle(g2, cx, cy, r);
                case "info" -> drawInfo(g2, cx, cy, r);
                default -> { }
            }
        }

        private static void drawShuffle(Graphics2D g2, double cx, double cy, double r) {
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double xl = cx - r;
            double xr = cx + r * 0.5;
            double yt = cy - r * 0.62;
            double yb = cy + r * 0.62;
            Path2D p1 = new Path2D.Double();
            p1.moveTo(xl, yb);
            p1.curveTo(cx - r * 0.15, yb, cx + r * 0.1, yt, xr, yt);
            g2.draw(p1);
            arrowHead(g2, xr, yt, r);
            Path2D p2 = new Path2D.Double();
            p2.moveTo(xl, yt);
            p2.curveTo(cx - r * 0.15, yt, cx + r * 0.1, yb, xr, yb);
            g2.draw(p2);
            arrowHead(g2, xr, yb, r);
        }

        private static void arrowHead(Graphics2D g2, double x, double y, double r) {
            double aLen = r * 0.6;
            double aw = r * 0.42;
            Path2D h = new Path2D.Double();
            h.moveTo(x + aLen, y);
            h.lineTo(x - aLen * 0.2, y - aw);
            h.lineTo(x - aLen * 0.2, y + aw);
            h.closePath();
            g2.fill(h);
        }

        private static void drawInfo(Graphics2D g2, double cx, double cy, double r) {
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            double dotR = r * 0.16;
            g2.fill(new Ellipse2D.Double(cx - dotR, cy - r * 0.55 - dotR, dotR * 2, dotR * 2));
            double bw = r * 0.28;
            g2.fill(new Rectangle2D.Double(cx - bw / 2, cy - r * 0.15, bw, r * 0.72));
        }

        private static void drawOne(Graphics2D g2, double cx, double cy, double r) {
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, (float) (r * 1.25)));
            java.awt.FontMetrics fm = g2.getFontMetrics();
            String s = "1";
            g2.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0),
                    (float) (cy + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
        }

        private static Path2D triangleRight(double baseX, double cy, double r) {
            Path2D p = new Path2D.Double();
            p.moveTo(baseX, cy - r);
            p.lineTo(baseX, cy + r);
            p.lineTo(baseX + r * 1.2, cy);
            p.closePath();
            return p;
        }

        private static Path2D triangleLeft(double baseX, double cy, double r) {
            Path2D p = new Path2D.Double();
            p.moveTo(baseX, cy - r);
            p.lineTo(baseX, cy + r);
            p.lineTo(baseX - r * 1.2, cy);
            p.closePath();
            return p;
        }

        private static void drawLoop(Graphics2D g2, double cx, double cy, double r) {
            double left = cx - r;
            double right = cx + r;
            double top = cy - r;
            double bot = cy + r;
            double cr = r * 0.6;
            double gapL = cx - r * 0.35;
            double gapR = cx + r * 0.2;

            g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Double();
            p.moveTo(gapR, top);
            p.lineTo(right - cr, top);
            p.quadTo(right, top, right, top + cr);
            p.lineTo(right, bot - cr);
            p.quadTo(right, bot, right - cr, bot);
            p.lineTo(left + cr, bot);
            p.quadTo(left, bot, left, bot - cr);
            p.lineTo(left, top + cr);
            p.quadTo(left, top, left + cr, top);
            p.lineTo(gapL, top);
            g2.draw(p);

            double aLen = r * 0.85;
            double aw = r * 0.5;
            Path2D head = new Path2D.Double();
            head.moveTo(gapL + aLen, top);
            head.lineTo(gapL, top - aw);
            head.lineTo(gapL, top + aw);
            head.closePath();
            g2.fill(head);
        }
    }
}
