/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.selection;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Click-to-place-vertex polygon lasso: each call to {@link #addVertex}
 * places another vertex, {@link #undoLastVertex} removes the most recent
 * one, and clicking near the first vertex (with at least 3 vertices already
 * placed) closes the polygon via {@link #tryClose}. This is deliberately
 * NOT a mouse-drag freehand tool — a hand-drawn freehand trace is not
 * something most people, including Walter, can produce a clean selection
 * with. Click-polygon is the one and only lasso interaction this module
 * supports.
 *
 * <p>
 * Once {@link #isClosed()}, {@link #getVertices()} is a source-space vertex
 * list ready for {@link nl.wers.library.images.BitSet2D#createFromPolygon}.
 */
public class VertexLasso {

    private final PointMapper mapper;
    private final Color color;
    private final List<Point> vertices = new ArrayList<>();
    private final RubberBandLine nextVertexGuide;
    private boolean closed;

    public VertexLasso(PointMapper mapper, Color color) {
        this.mapper = mapper;
        this.color = color;
        this.nextVertexGuide = new RubberBandLine(mapper, color);
    }

    public void addVertex(Point sourcePoint) {
        if (closed) {
            return;
        }
        vertices.add(sourcePoint);
        nextVertexGuide.begin(sourcePoint);
    }

    public boolean undoLastVertex() {
        if (closed || vertices.isEmpty()) {
            return false;
        }
        vertices.remove(vertices.size() - 1);
        if (vertices.isEmpty()) {
            nextVertexGuide.clearAnchor();
        } else {
            nextVertexGuide.begin(vertices.get(vertices.size() - 1));
        }
        return true;
    }

    /**
     * @param candidate the point the user just clicked, in source space
     * @param closeToleranceSourcePixels how close to the first vertex counts
     * as "closing the loop"
     * @return true if this click closed the polygon (caller should stop
     * calling {@link #addVertex} after this)
     */
    public boolean tryClose(Point candidate, int closeToleranceSourcePixels) {
        if (closed || vertices.size() < 3) {
            return false;
        }
        Point first = vertices.get(0);
        double dx = candidate.x - first.x;
        double dy = candidate.y - first.y;
        if (Math.sqrt(dx * dx + dy * dy) <= closeToleranceSourcePixels) {
            closed = true;
            return true;
        }
        return false;
    }

    public boolean isClosed() {
        return closed;
    }

    public List<Point> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    public void reset() {
        vertices.clear();
        closed = false;
    }

    public void updateCursor(Graphics2D g, Point sourcePoint) {
        if (!closed && !vertices.isEmpty()) {
            nextVertexGuide.update(g, sourcePoint);
        }
    }

    /**
     * Draws the placed segments (not the live cursor guide — see
     * {@link #updateCursor}) over {@code g}.
     */
    public void drawPlacedSegments(Graphics2D g) {
        if (vertices.size() < 2) {
            return;
        }
        List<Point> componentSpace = new ArrayList<>(vertices.size());
        for (Point p : vertices) {
            componentSpace.add(mapper.toComponent(p));
        }
        XorDrawing.drawPolyline(g, componentSpace, color);
    }
}
