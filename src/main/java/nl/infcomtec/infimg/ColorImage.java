/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.infimg;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Panel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.imageio.ImageIO;

/**
 * A decoded image paired with its colour analysis and load timing.
 * <p>
 * Trimmed for PixelInspector from the full Voynich {@code ColorImage}: the
 * mask-aware constructor, {@link #thumbnailMask}, and their {@code BitSet2D}
 * dependency were dropped since this tool always inspects a whole,
 * already-decoded image with no region masking. Everything else — the
 * {@link ColorBase} pixel inventory, LAB index, and thumbnail/ΔE machinery —
 * is unchanged from the source.
 * </p>
 */
public class ColorImage {

    public static final int THUMB_SIZE = 256;

    private static final Panel TRACKER_COMPONENT = new Panel();

    /**
     * The colour base holding the per-image cache and pixel counts.
     */
    public ColorBase cb = new ColorBase();
    public int w;
    public int h;
    /**
     * Flat pixel array in row-major order, length {@code w * h}. Each element
     * is the alpha-premultiplied absolute RGB value returned by
     * {@link ColorBase#add(int)} for the corresponding source pixel.
     */
    public int[] pixels;
    public long loadNanos;
    /**
     * CIELab-keyed index of all colours in this image.
     */
    public TreeMap<TriElm, ColorBase.TriLabColor> labIndex;

    public BufferedImage thumbnail;
    public ColorBase.TriLabColor[] labThumbnail;
    public int thumbnailUniqueColors;

    public ColorImage(File f) throws IOException {
        this(ImageIO.read(f), f.toString());
    }

    public ColorImage(BufferedImage img, String label) throws IOException {
        long t0 = System.nanoTime();
        w = img.getWidth();
        h = img.getHeight();
        pixels = img.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = cb.add(pixels[i]);
        }
        labIndex = new TreeMap<>();
        for (ColorBase.TriLabColor tc : cb.cache.values()) {
            TriElm key = new TriElm();
            key.v0 = tc.l;
            key.v1 = tc.a;
            key.v2 = tc.b;
            ColorBase.TriLabColor col = labIndex.get(key);
            if (null != col) {
                ColorBase.TriLabColor nw = new ColorBase.TriLabColor();
                nw.a = tc.a;
                nw.b = tc.b;
                nw.count = col.count + tc.count;
                nw.l = tc.l;
                nw.v0 = col.v0;
                nw.v1 = col.v1;
                nw.v2 = col.v2;
                labIndex.put(key, nw);
            } else {
                labIndex.put(key, tc);
            }
        }
        buildThumbnails(img, label);
        loadNanos = System.nanoTime() - t0;
    }

    private void buildThumbnails(BufferedImage img, String label) throws IOException {
        double scale = Math.min((double) THUMB_SIZE / w, (double) THUMB_SIZE / h);
        int scaledW = Math.max(1, (int) Math.round(w * scale));
        int scaledH = Math.max(1, (int) Math.round(h * scale));
        Image scaled = img.getScaledInstance(scaledW, scaledH, Image.SCALE_SMOOTH);
        MediaTracker tracker = new MediaTracker(TRACKER_COMPONENT);
        tracker.addImage(scaled, 0);
        try {
            tracker.waitForID(0);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while scaling thumbnail for " + label, ie);
        }
        BufferedImage thumb = new BufferedImage(THUMB_SIZE, THUMB_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = thumb.createGraphics();
        int offX = (THUMB_SIZE - scaledW) / 2;
        int offY = (THUMB_SIZE - scaledH) / 2;
        g2.drawImage(scaled, offX, offY, null);
        g2.dispose();
        thumbnail = thumb;

        labThumbnail = new ColorBase.TriLabColor[THUMB_SIZE * THUMB_SIZE];
        int[] thumbPixels = thumb.getRGB(0, 0, THUMB_SIZE, THUMB_SIZE, null, 0, THUMB_SIZE);
        TreeSet<TriElm> distinct = new TreeSet<>();
        for (int i = 0; i < thumbPixels.length; i++) {
            labThumbnail[i] = ColorBase.resolve(new Color(thumbPixels[i]));
            distinct.add(labThumbnail[i]);
        }
        thumbnailUniqueColors = distinct.size();
    }

    @Override
    public String toString() {
        long ms = loadNanos / 1_000_000L;
        long us = (loadNanos % 1_000_000L) / 1_000L;
        return ms > 0
                ? String.format("%dx%d, %d unique colours, %d pixels, %d ms", w, h, cb.cache.size(), pixels.length, ms)
                : String.format("%dx%d, %d unique colours, %d pixels, %d µs", w, h, cb.cache.size(), pixels.length, us);
    }

    /**
     * Returns all colours in this image sorted by ascending pixel count
     * (rarest first).
     */
    public List<ColorBase.TriLabColor> sortedByFrequency() {
        List<ColorBase.TriLabColor> list = new ArrayList<>(cb.cache.values());
        list.sort(new Comparator<ColorBase.TriLabColor>() {
            @Override
            public int compare(ColorBase.TriLabColor o1, ColorBase.TriLabColor o2) {
                int cmp = Long.compare(o1.count, o2.count);
                if (0 == cmp) {
                    return o1.compareTo(o2);
                }
                return cmp;
            }
        });
        return list;
    }

}
