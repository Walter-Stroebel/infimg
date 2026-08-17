package nl.infcomtec.infimg.pixelmicroscope;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import nl.infcomtec.infimg.ColorImage;

/**
 * Shows the (2*radius+1)x(2*radius+1) pixel neighbourhood around the point
 * last reported by {@link BigImagePanel}, each cell drawn as a filled swatch
 * with a thin grid line. Cells outside the image bounds are drawn as a hatch
 * pattern rather than clamped or wrapped. Radius+1 as the grid unit is a
 * cheap trick to always land on an odd size with a true centre pixel.
 * <p>
 * A slider (in its own {@link APanel} strip at the top, GridBagLayout) lets
 * the radius range from a tight, easily recognisable neighbourhood to a wide
 * exploded view without a fixed jump between the two, and without eating
 * fixed screen space when not in use — the grid itself always gets whatever
 * is left. Clicking a cell re-centres {@link BigImagePanel} on that pixel —
 * this grid doubles as a micro-navigation control, not just a readout.
 * </p>
 */
public class NeighborhoodPanel extends APanel {

    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 100;
    private static final int DEFAULT_RADIUS = 6;
    private static final int MIN_CELL = 14;

    private final JSlider radiusSlider = new JSlider(MIN_RADIUS, MAX_RADIUS, DEFAULT_RADIUS);
    private final JLabel radiusLabel = new JLabel();
    private final GridPanel grid = new GridPanel();

    private ColorImage image;
    private int cx = -1;
    private int cy = -1;
    private BigImagePanel navigator;

    public NeighborhoodPanel() {
        APanel sliderBar = new APanel();
        sliderBar.add(radiusLabel, GBCompass.west());
        sliderBar.add(radiusSlider, GBCompass.center());
        radiusSlider.addChangeListener(new RadiusChangeListener());
        updateRadiusLabel();

        add(sliderBar, GBCompass.north());
        add(grid, GBCompass.center());
    }

    public void setNavigator(BigImagePanel navigator) {
        this.navigator = navigator;
    }

    public void setCenter(ColorImage image, int px, int py) {
        this.image = image;
        this.cx = px;
        this.cy = py;
        grid.repaint();
    }

    private int radius() {
        return radiusSlider.getValue();
    }

    private int gridDim() {
        return 2 * radius() + 1;
    }

    private void updateRadiusLabel() {
        int r = radiusSlider.getValue();
        radiusLabel.setText(String.format("Radius %2d (%dx%d): ", r, 2 * r + 1, 2 * r + 1));
    }

    private final class RadiusChangeListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent e) {
            updateRadiusLabel();
            grid.revalidate();
            grid.repaint();
        }
    }

    private final class GridPanel extends JPanel {

        GridPanel() {
            setPreferredSize(new Dimension(3 * MIN_CELL, 3 * MIN_CELL));
            setBackground(Color.BLACK);
            ClickHandler clickHandler = new ClickHandler();
            addMouseListener(clickHandler);
            addMouseMotionListener(clickHandler);
            addMouseWheelListener(new WheelHandler());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (null == image || cx < 0 || cy < 0) {
                return;
            }
            int dim = gridDim();
            int radius = radius();
            Graphics2D g2 = (Graphics2D) g;
            // Cells stretch to fill the panel exactly on both axes — no
            // leftover black margin from integer-dividing a fixed square
            // cell size into the panel's actual (non-multiple) dimensions.
            double cellW = (double) getWidth() / dim;
            double cellH = (double) getHeight() / dim;
            for (int row = 0; row < dim; row++) {
                int iy = cy - radius + row;
                int y = (int) Math.round(row * cellH);
                int yNext = (int) Math.round((row + 1) * cellH);
                for (int col = 0; col < dim; col++) {
                    int ix = cx - radius + col;
                    int x = (int) Math.round(col * cellW);
                    int xNext = (int) Math.round((col + 1) * cellW);
                    int w = xNext - x;
                    int h = yNext - y;
                    if (ix >= 0 && iy >= 0 && ix < image.w && iy < image.h) {
                        int rgb = image.pixels[iy * image.w + ix];
                        g2.setColor(new Color(rgb));
                        g2.fillRect(x, y, w, h);
                    } else {
                        paintHatch(g2, x, y, w, h);
                    }
                    g2.setColor(Color.GRAY);
                    g2.drawRect(x, y, w, h);
                }
            }
            g2.setColor(Color.RED);
            int cxPix = (int) Math.round(radius * cellW);
            int cyPix = (int) Math.round(radius * cellH);
            int cw = (int) Math.round((radius + 1) * cellW) - cxPix;
            int ch = (int) Math.round((radius + 1) * cellH) - cyPix;
            g2.drawRect(cxPix, cyPix, cw, ch);
            g2.drawRect(cxPix + 1, cyPix + 1, cw - 2, ch - 2);
        }

        private void paintHatch(Graphics2D g2, int x, int y, int w, int h) {
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect(x, y, w, h);
            g2.setColor(Color.GRAY);
            int span = Math.max(w, h);
            for (int d = -span; d < span; d += 6) {
                g2.drawLine(x + d, y, x + d + span, y + span);
            }
        }

        /**
         * A click jumps straight to the clicked cell (absolute positioning —
         * the obvious behaviour for a single click). A drag instead pans
         * like a microscope stage: the image moves opposite to the hand, so
         * dragging down reveals what's above, the same convention as
         * dragging a map or a photo canvas. That means drag deltas are
         * tracked and applied inverted, relative to the previous drag event
         * — not computed as an absolute "jump to the cell under the cursor,"
         * which is what a click does and reads backwards when used for a
         * drag gesture.
         */
        private final class ClickHandler extends MouseAdapter {

            private int lastDragX;
            private int lastDragY;

            @Override
            public void mousePressed(MouseEvent e) {
                lastDragX = e.getX();
                lastDragY = e.getY();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                navigateToCell(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (null == image || null == navigator || cx < 0 || cy < 0) {
                    return;
                }
                int dim = gridDim();
                double cellW = (double) getWidth() / dim;
                double cellH = (double) getHeight() / dim;
                int dCols = (int) Math.round((e.getX() - lastDragX) / cellW);
                int dRows = (int) Math.round((e.getY() - lastDragY) / cellH);
                if (0 == dCols && 0 == dRows) {
                    return;
                }
                lastDragX = e.getX();
                lastDragY = e.getY();
                navigator.navigateTo(cx - dCols, cy - dRows);
            }

            private void navigateToCell(MouseEvent e) {
                if (null == image || null == navigator || cx < 0 || cy < 0) {
                    return;
                }
                int dim = gridDim();
                int radius = radius();
                double cellW = (double) getWidth() / dim;
                double cellH = (double) getHeight() / dim;
                int col = (int) (e.getX() / cellW);
                int row = (int) (e.getY() / cellH);
                if (col < 0 || row < 0 || col >= dim || row >= dim) {
                    return;
                }
                int ix = cx - radius + col;
                int iy = cy - radius + row;
                navigator.navigateTo(ix, iy);
            }
        }

        /**
         * Mouse wheel over the grid adjusts the radius slider — a habit
         * reflex from every other zoom-capable tool, wired directly to the
         * slider's own value so label/repaint stay driven by the one
         * {@link RadiusChangeListener} rather than a second code path.
         */
        private final class WheelHandler implements MouseWheelListener {

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int delta = -e.getWheelRotation();
                int next = radiusSlider.getValue() + delta;
                radiusSlider.setValue(Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, next)));
            }
        }
    }
}
