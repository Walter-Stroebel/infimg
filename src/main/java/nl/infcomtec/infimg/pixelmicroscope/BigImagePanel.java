package nl.infcomtec.infimg.pixelmicroscope;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.swing.JPanel;
import nl.infcomtec.infimg.ColorImage;

/**
 * Displays a loaded image scaled to fit (integer nearest-neighbour scale,
 * preserving aspect ratio) and reports the pixel under the mouse to the
 * neighbourhood and info panels. Draws its own XOR crosshair across the full
 * panel as a ruler; the system cursor is left alone.
 * <p>
 * Hovering tracks the pixel under the mouse live. Clicking toggles a lock on
 * the current pixel — needed because moving the mouse off the image (e.g. to
 * read the neighbourhood or info panel) would otherwise immediately lose the
 * pixel under inspection. Click again on the locked pixel to unlock and
 * resume live tracking; clicking a different pixel while locked re-locks
 * there.
 * </p>
 */
public class BigImagePanel extends JPanel {

    /** Minimum on-screen pixel size, in device pixels, before per-pixel gridlines are drawn. */
    private static final double GRID_MIN_SCALE = 4;

    private final NeighborhoodPanel neighborhood;
    private final PixelInfoPanel info;

    private ColorImage image;
    private double scale = 1;
    private double offX;
    private double offY;
    private int lastPx = -1;
    private int lastPy = -1;
    private boolean locked;

    public BigImagePanel(NeighborhoodPanel neighborhood, PixelInfoPanel info) {
        this.neighborhood = neighborhood;
        this.info = info;
        setBackground(Color.DARK_GRAY);
        MouseTracker tracker = new MouseTracker();
        addMouseMotionListener(tracker);
        addMouseListener(tracker);
    }

    public void loadImage(File f) throws IOException {
        loadImage(new ColorImage(f));
    }

    /**
     * Loads an already-decoded image, e.g. a live {@code BufferedImage} held
     * by another app's canvas (clipboard-pasted, with no backing file) —
     * infimg's "Pixel Microscope" menu item uses this rather than round-
     * tripping through a temp file just to satisfy {@link #loadImage(File)}.
     */
    public void loadImage(java.awt.image.BufferedImage img, String label) throws IOException {
        loadImage(new ColorImage(img, label));
    }

    private void loadImage(ColorImage newImage) {
        image = newImage;
        cachedDisplay = null;
        lastPx = -1;
        lastPy = -1;
        locked = false;
        revalidate();
        repaint();
    }

    private void recomputeLayout() {
        if (null == image) {
            return;
        }
        int availW = Math.max(1, getWidth());
        int availH = Math.max(1, getHeight());
        double scaleW = (double) availW / image.w;
        double scaleH = (double) availH / image.h;
        scale = Math.min(scaleW, scaleH);
        offX = (availW - image.w * scale) / 2;
        offY = (availH - image.h * scale) / 2;
    }

    private void updatePixel(int px, int py) {
        if (null == image || px < 0 || py < 0 || px >= image.w || py >= image.h) {
            return;
        }
        if (px == lastPx && py == lastPy) {
            return;
        }
        lastPx = px;
        lastPy = py;
        neighborhood.setCenter(image, px, py);
        info.setPixel(image, px, py);
        repaint();
    }

    /**
     * Maps a panel-space coordinate to the pixel column/row it falls in,
     * using the same {@code round(offX/offY + n*scale)} boundary function
     * {@link #paintComponent} uses to draw gridlines and the image itself —
     * a separately-derived {@code floor((mouseX-offX)/scale)} formula can
     * disagree with the drawn boundaries by one pixel near the edges once
     * {@code scale} is fractional and {@code image.w}/{@code image.h} are
     * large, since each independently accumulates rounding error.
     */
    private int panelToImageCoord(double panelCoord, double off, int extent) {
        int lo = 0;
        int hi = extent;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            double boundary = Math.round(off + mid * scale);
            if (boundary <= panelCoord) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return Math.min(lo, extent - 1);
    }

    private void handleClick(int px, int py) {
        if (null == image || px < 0 || py < 0 || px >= image.w || py >= image.h) {
            return;
        }
        if (locked && px == lastPx && py == lastPy) {
            locked = false;
            return;
        }
        locked = true;
        updatePixel(px, py);
    }

    /**
     * Re-centres on {@code (px, py)} and locks there, driven by a click in
     * {@link NeighborhoodPanel} — lets the neighbourhood grid act as a
     * micro-navigation control instead of a pure readout. Unlike a click on
     * the big image itself, clicking the already-selected centre cell is a
     * no-op rather than an unlock toggle.
     */
    public void navigateTo(int px, int py) {
        if (null == image || px < 0 || py < 0 || px >= image.w || py >= image.h) {
            return;
        }
        locked = true;
        updatePixel(px, py);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (null == image) {
            return;
        }
        recomputeLayout();
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                (scale >= 1)
                        ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int dispW = (int) Math.round(image.w * scale);
        int dispH = (int) Math.round(image.h * scale);
        int ix = (int) Math.round(offX);
        int iy = (int) Math.round(offY);
        g2.drawImage(toDisplayImage(), ix, iy, dispW, dispH, null);

        if (scale >= GRID_MIN_SCALE) {
            g2.setColor(new Color(0, 0, 0, 60));
            for (int x = 0; x <= image.w; x++) {
                int sx = (int) Math.round(offX + x * scale);
                g2.drawLine(sx, iy, sx, iy + dispH);
            }
            for (int y = 0; y <= image.h; y++) {
                int sy = (int) Math.round(offY + y * scale);
                g2.drawLine(ix, sy, ix + dispW, sy);
            }
        }

        if (lastPx >= 0 && lastPy >= 0) {
            int cx = (int) Math.round(offX + lastPx * scale + scale / 2);
            int cy = (int) Math.round(offY + lastPy * scale + scale / 2);
            g2.setXORMode(locked ? Color.RED : Color.WHITE);
            g2.drawLine(0, cy, getWidth(), cy);
            g2.drawLine(cx, 0, cx, getHeight());
            g2.setPaintMode();
        }
    }

    private BufferedImage cachedDisplay;

    private BufferedImage toDisplayImage() {
        if (null != cachedDisplay) {
            return cachedDisplay;
        }
        cachedDisplay = new BufferedImage(image.w, image.h, BufferedImage.TYPE_INT_RGB);
        cachedDisplay.setRGB(0, 0, image.w, image.h, image.pixels, 0, image.w);
        return cachedDisplay;
    }

    private final class MouseTracker extends MouseAdapter {

        @Override
        public void mouseMoved(MouseEvent e) {
            if (locked) {
                return;
            }
            handle(e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (null == image) {
                return;
            }
            int px = panelToImageCoord(e.getX(), offX, image.w);
            int py = panelToImageCoord(e.getY(), offY, image.h);
            handleClick(px, py);
        }

        private void handle(MouseEvent e) {
            if (null == image) {
                return;
            }
            int px = panelToImageCoord(e.getX(), offX, image.w);
            int py = panelToImageCoord(e.getY(), offY, image.h);
            updatePixel(px, py);
        }
    }
}
