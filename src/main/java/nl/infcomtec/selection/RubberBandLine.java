/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.selection;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;

/**
 * A single live XOR-mode guide line from a fixed anchor to a moving point
 * (e.g. "next vertex" preview while placing a lasso, or one edge of a
 * dragged rectangle). Tracks its own last-drawn endpoint so a caller can
 * erase-and-redraw on every mouse move without hand-rolling that bookkeeping
 * at each call site.
 */
public class RubberBandLine {

    private final PointMapper mapper;
    private final Color color;
    private Point anchor;
    private Point lastDrawnTo;

    public RubberBandLine(PointMapper mapper, Color color) {
        this.mapper = mapper;
        this.color = color;
    }

    public void begin(Point anchorSourcePoint) {
        this.anchor = anchorSourcePoint;
        this.lastDrawnTo = null;
    }

    /**
     * Clears the anchor entirely, so {@link #update} becomes a no-op until
     * {@link #begin} is called again. Use instead of {@code begin(null)}
     * when there is no next anchor (e.g. all vertices undone).
     */
    public void clearAnchor() {
        this.anchor = null;
        this.lastDrawnTo = null;
    }

    public void update(Graphics2D g, Point toSourcePoint) {
        if (anchor == null) {
            return;
        }
        erase(g);
        Point from = mapper.toComponent(anchor);
        Point to = mapper.toComponent(toSourcePoint);
        XorDrawing.drawLine(g, from, to, color);
        lastDrawnTo = toSourcePoint;
    }

    public void erase(Graphics2D g) {
        if (anchor == null || lastDrawnTo == null) {
            return;
        }
        Point from = mapper.toComponent(anchor);
        Point to = mapper.toComponent(lastDrawnTo);
        XorDrawing.drawLine(g, from, to, color);
        lastDrawnTo = null;
    }

    public void end(Graphics2D g) {
        erase(g);
        anchor = null;
    }
}
