/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.selection;

import java.awt.Point;

/**
 * Converts a point from a caller's own source/image coordinate space into
 * that caller's live component/panel coordinate space, so the rest of this
 * module can stay coordinate-system-agnostic. Every selection primitive here
 * keeps its state (vertices, corners, candidate regions) in source space and
 * calls a {@link PointMapper} only at the moment it needs to draw — this is
 * the seam that lets one implementation serve very different rendering
 * surfaces (a custom {@code JComponent} with its own pan/zoom transform, a
 * plain {@code JLabel} showing a scaled {@code ImageIcon}, etc.) without
 * duplicating any drawing or hit-testing logic per surface.
 */
public interface PointMapper {

    Point toComponent(Point sourcePoint);
}
