/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.selection;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import nl.wers.library.images.BitSet2D;

/**
 * Ways this module knows to produce a {@link BitSet2D} mask — rectangle and
 * closed polygon (from {@link VertexLasso}) — funneled into the same output
 * type. A caller never needs to know which tool produced a given mask;
 * {@link #fromRectangle} is simply the degenerate 4-point case of
 * {@link BitSet2D#createFromPolygon}, not a separate code path.
 * <p>
 * The catalog module's original also has a {@code fromFloodFill} (magic-wand)
 * factory backed by {@code ColorToleranceCondition} — deliberately not
 * vendored here: infimg's selection feature is Rectangle/Lasso only, per
 * Walter's explicit scope call ("No magic wand, that's a nice 'extra catch'
 * but the core lasso/rect refinement... is new to it").
 */
public final class SelectionMasks {

    private SelectionMasks() {
    }

    /**
     * @return {@code r}'s four corners, clockwise from top-left, as a
     * polygon vertex list ready for {@link BitSet2D#createFromPolygon}
     */
    public static List<Point> rectangleVertices(Rectangle r) {
        List<Point> vertices = new ArrayList<>(4);
        vertices.add(new Point(r.x, r.y));
        vertices.add(new Point(r.x + r.width, r.y));
        vertices.add(new Point(r.x + r.width, r.y + r.height));
        vertices.add(new Point(r.x, r.y + r.height));
        return vertices;
    }

    public static BitSet2D fromRectangle(Rectangle r, int width, int height) {
        return BitSet2D.createFromPolygon(rectangleVertices(r), width, height);
    }

    public static BitSet2D fromPolygon(List<Point> vertices, int width, int height) {
        return BitSet2D.createFromPolygon(vertices, width, height);
    }
}
