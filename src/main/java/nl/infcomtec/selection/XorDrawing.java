/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.selection;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

/**
 * Stateless XOR-mode drawing primitives for live selection feedback: drawing
 * the same shape twice in {@link Graphics2D#setXORMode} restores the pixels
 * underneath, so a caller can erase-and-redraw a cursor/rubber-band on every
 * mouse move without repainting the whole component. XOR also stays visible
 * against arbitrary content underneath, unlike a fixed solid color.
 *
 * <p>
 * Every method here takes the {@link Graphics2D} the caller already has (from
 * {@code Component.getGraphics()} or a paint callback) and restores
 * {@link Graphics2D#setPaintMode()} before returning; it never calls
 * {@code dispose()} — the caller owns that {@code Graphics2D}'s lifecycle.
 */
public final class XorDrawing {

    private XorDrawing() {
    }

    public static void drawLine(Graphics2D g, Point a, Point b, Color color) {
        g.setXORMode(Color.WHITE);
        g.setColor(color);
        g.drawLine(a.x, a.y, b.x, b.y);
        g.setPaintMode();
    }

    public static void drawRect(Graphics2D g, Rectangle r, Color color) {
        g.setXORMode(Color.WHITE);
        g.setColor(color);
        g.drawRect(r.x, r.y, r.width, r.height);
        g.setPaintMode();
    }

    /**
     * Draws the open polyline through {@code points} in order (does not close
     * back to the first point — callers that want a closed polygon outline
     * should append the first point to {@code points} themselves).
     */
    public static void drawPolyline(Graphics2D g, List<Point> points, Color color) {
        if (points.size() < 2) {
            return;
        }
        g.setXORMode(Color.WHITE);
        g.setColor(color);
        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i - 1);
            Point b = points.get(i);
            g.drawLine(a.x, a.y, b.x, b.y);
        }
        g.setPaintMode();
    }

    public static void drawCrosshair(Graphics2D g, Point center, int halfSize, Color color) {
        g.setXORMode(Color.WHITE);
        g.setColor(color);
        g.drawLine(center.x - halfSize, center.y, center.x + halfSize, center.y);
        g.drawLine(center.x, center.y - halfSize, center.x, center.y + halfSize);
        g.setPaintMode();
    }
}
