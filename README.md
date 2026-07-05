# Java-AWT-Swing-Fun

A small Swing playground: a field of drifting particles you can reshape, recolour,
resize, and scatter. Every option lives in the footer control bar.

## Controls

The footer has a **player bar** on top and the particle controls grouped below it.

Particle controls, by group:

- **Look** — Shape (circles/squares), Colour (Rainbow/Confetti/Mono), Ball Size,
  Background (mix R/G/B), Smoothing (anti-aliasing)
- **Motion** — Count, Drift Speed, Edges (Bounce/Respawn/Wrap)
- **Burst** — Burst Min / Max (click-scatter speed range; equal = even ring),
  Auto-Scatter (periodic explosion from a random point)
- **Effects** — Colour Cycling, Trails (comet fade), Pulse (size breathing)

**Mouse** — click the canvas to burst all particles out from that point.

## Beat Sync

The player bar loads a song and scatters the particles in time with it, like a
mini music player:

- **Load Song** — pick an `mp3`, `wav`, `aiff`, or `au` file; playback starts
  automatically and the field bursts from a random point on every detected beat
- Album art, title, and artist are read from the file's tags and shown on the left
- **Play/pause**, **back/forward 10 s**, and a **scrubber** to seek

Beats are found by energy-flux onset detection over the decoded audio, then fired
in sync with playback. The first ~1 second is a warm-up window and won't trigger
bursts.

MP3 decoding (and tag/art reading) uses the JLayer / mp3spi / tritonus-share
libraries, pulled from Maven Central by Gradle — WAV/AIFF/AU need only the JDK.

## Run

This is a Gradle project; the wrapper fetches Gradle and all dependencies on first
run, so nothing needs to be installed globally except a JDK.

```sh
./gradlew run
```

Other useful tasks:

```sh
./gradlew build          # compile + assemble
./gradlew installDist    # produces a runnable launcher under build/install/
```

A graphical (non-headless) JDK is required — on Debian/Ubuntu install `openjdk-25-jdk`.

## Project layout

```text
build.gradle              build config + dependencies
settings.gradle
gradlew / gradlew.bat     Gradle wrapper (no global install needed)
src/main/java/io/github/joni1695/javaawtswingfun/
    app/                  entry point + UI
        Main              launches the app
        ParticlePlayground  Swing frame, controls, and player bar
    canvas/               the particle field
        ParticleCanvas    field + rendering
        Particle          a single particle's motion/colour
        ColorMode / EdgeMode / ShapeType
    audio/                parsing + analysis (no playback)
        BeatDetector      energy-flux onset detection
        AudioMeta         ID3 tag + album-art reader
    player/               playback
        BeatPlayer        decode, transport, beat timing
```

The packages depend inward: `app` → `player` / `canvas` / `audio`, and
`player` → `audio`. `canvas` and `audio` have no dependencies on the rest.
