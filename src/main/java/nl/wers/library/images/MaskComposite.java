/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.wers.library.images;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Applies a {@link BitSet2D} mask to a source image: pixels inside the mask
 * are kept as-is, pixels outside are replaced with a solid color. Always
 * produces an opaque {@code TYPE_INT_RGB} result — any alpha channel on the
 * source is stripped, not carried forward, so a caller composing a
 * "selection, everything else blacked out" result never inherits stray
 * pre-existing transparency from the source image.
 */
public final class MaskComposite {

    private MaskComposite() {
    }

    /**
     * @param source the image to composite; not mutated
     * @param mask which pixels of {@code source} to keep; must be at least
     * {@code source}'s size
     * @param outsideColor solid color for every pixel outside {@code mask}
     * @return a new, opaque image the same size as {@code source}
     */
    public static BufferedImage applySolidOutside(BufferedImage source, BitSet2D mask, Color outsideColor) {
        int w = source.getWidth();
        int h = source.getHeight();
        int outsideRgb = outsideColor.getRGB() & 0xFFFFFF;
        int[] pixels = source.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                if (mask.get2D(x, y)) {
                    pixels[rowOffset + x] &= 0xFFFFFF;
                } else {
                    pixels[rowOffset + x] = outsideRgb;
                }
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }
}
