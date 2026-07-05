package io.github.joni1695.javaawtswingfun.audio;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class AudioMeta {

    public record SyncedLine(long timeMs, String text) { }

    public final String title;
    public final String artist;
    public final String album;
    public final BufferedImage art;
    public final String lyrics;
    public final List<SyncedLine> syncedLyrics;

    private AudioMeta(String title, String artist, String album, BufferedImage art,
            String lyrics, List<SyncedLine> syncedLyrics) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.art = art;
        this.lyrics = lyrics;
        this.syncedLyrics = syncedLyrics;
    }

    public static AudioMeta read(java.io.File file) {
        String fallback = stripExtension(file.getName());
        try (InputStream raw = new FileInputStream(file)) {
            DataInputStream in = new DataInputStream(raw);
            byte[] header = new byte[10];
            in.readFully(header);
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
                return new AudioMeta(fallback, "", "", null, "", List.of());
            }
            int major = header[3] & 0xFF;
            int flags = header[5] & 0xFF;
            int size = synchsafe(header, 6);
            byte[] body = new byte[size];
            in.readFully(body);
            if ((flags & 0x80) != 0) {
                body = deunsynchronise(body);
            }
            return parse(major, body, fallback);
        } catch (Exception e) {
            return new AudioMeta(fallback, "", "", null, "", List.of());
        }
    }

    public boolean hasLyrics() {
        return !syncedLyrics.isEmpty() || (lyrics != null && !lyrics.isBlank());
    }

    private static AudioMeta parse(int major, byte[] body, String fallback) {
        String title = "";
        String artist = "";
        String album = "";
        String lyrics = "";
        BufferedImage art = null;
        List<SyncedLine> synced = new ArrayList<>();
        boolean frontCover = false;

        int headerSize = major == 2 ? 6 : 10;
        int idLen = major == 2 ? 3 : 4;
        int pos = 0;
        while (pos + headerSize <= body.length) {
            String id = new String(body, pos, idLen, StandardCharsets.ISO_8859_1);
            if (id.charAt(0) == 0) {
                break;
            }
            int frameSize;
            if (major == 2) {
                frameSize = ((body[pos + 3] & 0xFF) << 16)
                        | ((body[pos + 4] & 0xFF) << 8)
                        | (body[pos + 5] & 0xFF);
            } else if (major == 4) {
                frameSize = synchsafe(body, pos + 4);
            } else {
                frameSize = ((body[pos + 4] & 0xFF) << 24)
                        | ((body[pos + 5] & 0xFF) << 16)
                        | ((body[pos + 6] & 0xFF) << 8)
                        | (body[pos + 7] & 0xFF);
            }
            pos += headerSize;
            if (frameSize <= 0 || pos + frameSize > body.length) {
                break;
            }
            byte[] data = new byte[frameSize];
            System.arraycopy(body, pos, data, 0, frameSize);
            pos += frameSize;

            switch (id) {
                case "TIT2", "TT2" -> title = decodeText(data);
                case "TPE1", "TP1" -> artist = decodeText(data);
                case "TALB", "TAL" -> album = decodeText(data);
                case "USLT", "ULT" -> lyrics = parseUslt(data);
                case "SYLT", "SLT" -> synced = parseSylt(data);
                case "APIC", "PIC" -> {
                    if (!frontCover) {
                        Picture pic = decodePicture(id, data);
                        if (pic != null && pic.image != null) {
                            art = pic.image;
                            frontCover = pic.type == 3;
                        }
                    }
                }
                default -> { }
            }
        }
        if (title.isBlank()) {
            title = fallback;
        }
        return new AudioMeta(title, artist, album, art, lyrics, List.copyOf(synced));
    }

    private static String parseUslt(byte[] d) {
        if (d.length < 5) {
            return "";
        }
        int enc = d[0] & 0xFF;
        int descEnd = findTerminator(d, 4, enc);
        int start = descEnd + terminatorLength(enc);
        if (start >= d.length) {
            return "";
        }
        return new String(d, start, d.length - start, charsetFor(enc)).replace("\0", "").strip();
    }

    private static List<SyncedLine> parseSylt(byte[] d) {
        List<SyncedLine> out = new ArrayList<>();
        if (d.length < 7) {
            return out;
        }
        int enc = d[0] & 0xFF;
        int timeFormat = d[4] & 0xFF;
        if (timeFormat != 2) {
            return out;
        }
        int p = findTerminator(d, 6, enc) + terminatorLength(enc);
        while (p < d.length) {
            int end = findTerminator(d, p, enc);
            String text = new String(d, p, end - p, charsetFor(enc)).replace("\0", "");
            p = end + terminatorLength(enc);
            if (p + 4 > d.length) {
                break;
            }
            long stamp = ((long) (d[p] & 0xFF) << 24) | ((d[p + 1] & 0xFF) << 16)
                    | ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
            p += 4;
            String line = text.replace("\r", "").replace("\n", "").strip();
            if (!line.isEmpty()) {
                out.add(new SyncedLine(stamp, line));
            }
        }
        return out;
    }

    private static int findTerminator(byte[] d, int off, int enc) {
        if (enc == 1 || enc == 2) {
            for (int i = off; i + 1 < d.length; i += 2) {
                if (d[i] == 0 && d[i + 1] == 0) {
                    return i;
                }
            }
        } else {
            for (int i = off; i < d.length; i++) {
                if (d[i] == 0) {
                    return i;
                }
            }
        }
        return d.length;
    }

    private static int terminatorLength(int enc) {
        return enc == 1 || enc == 2 ? 2 : 1;
    }

    private static Charset charsetFor(int enc) {
        return switch (enc) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
    }

    private record Picture(BufferedImage image, int type) { }

    private static Picture decodePicture(String id, byte[] d) {
        try {
            int p = 1;
            if (id.equals("PIC")) {
                p += 3;
            } else {
                while (p < d.length && d[p] != 0) {
                    p++;
                }
                p++;
            }
            int type = d[p] & 0xFF;
            p++;
            int enc = d[0] & 0xFF;
            if (enc == 1 || enc == 2) {
                while (p + 1 < d.length && !(d[p] == 0 && d[p + 1] == 0)) {
                    p++;
                }
                p += 2;
            } else {
                while (p < d.length && d[p] != 0) {
                    p++;
                }
                p++;
            }
            if (p >= d.length) {
                return null;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(d, p, d.length - p));
            return new Picture(img, type);
        } catch (Exception e) {
            return null;
        }
    }

    private static String decodeText(byte[] d) {
        if (d.length <= 1) {
            return "";
        }
        int enc = d[0] & 0xFF;
        int off = 1;
        Charset cs;
        if (enc == 1 || enc == 2) {
            Charset wide = StandardCharsets.UTF_16BE;
            if (d.length >= 3 && (d[1] & 0xFF) == 0xFF && (d[2] & 0xFF) == 0xFE) {
                wide = StandardCharsets.UTF_16LE;
                off = 3;
            } else if (d.length >= 3 && (d[1] & 0xFF) == 0xFE && (d[2] & 0xFF) == 0xFF) {
                wide = StandardCharsets.UTF_16BE;
                off = 3;
            }
            boolean bigEndian = wide == StandardCharsets.UTF_16BE;
            cs = looksWide(d, off, bigEndian) ? wide : StandardCharsets.ISO_8859_1;
        } else if (enc == 3) {
            cs = StandardCharsets.UTF_8;
        } else {
            cs = StandardCharsets.ISO_8859_1;
        }
        return new String(d, off, d.length - off, cs).replace("\0", "").trim();
    }

    private static boolean looksWide(byte[] d, int off, boolean bigEndian) {
        int highStart = bigEndian ? off : off + 1;
        int examined = 0;
        int zeros = 0;
        for (int i = highStart; i < d.length && examined < 8; i += 2) {
            examined++;
            if (d[i] == 0) {
                zeros++;
            }
        }
        return examined > 0 && zeros * 2 >= examined;
    }

    private static byte[] deunsynchronise(byte[] in) {
        byte[] out = new byte[in.length];
        int j = 0;
        for (int i = 0; i < in.length; i++) {
            out[j++] = in[i];
            if ((in[i] & 0xFF) == 0xFF && i + 1 < in.length && (in[i + 1] & 0xFF) == 0x00) {
                i++;
            }
        }
        byte[] trimmed = new byte[j];
        System.arraycopy(out, 0, trimmed, 0, j);
        return trimmed;
    }

    private static int synchsafe(byte[] b, int off) {
        return ((b[off] & 0x7F) << 21)
                | ((b[off + 1] & 0x7F) << 14)
                | ((b[off + 2] & 0x7F) << 7)
                | (b[off + 3] & 0x7F);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
