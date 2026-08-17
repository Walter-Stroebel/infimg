/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.infimg.pixelmicroscope;

import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * GBCompass provides BorderLayout-style named positions backed by
 * GridBagLayout. Each method returns a fresh GridBagConstraints instance —
 * never reuse the returned object, as GridBagLayout reads constraints at add()
 * time but holds no copy.
 */
public class GBCompass {

    private static final Insets STD_INSETS = new Insets(2, 2, 2, 2);

    /**
     * @return A fresh NORTH constraint.
     */
    public static GridBagConstraints north() {
        return create(0, 0, 3, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL, STD_INSETS);
    }

    /**
     * @return A fresh WEST constraint.
     */
    public static GridBagConstraints west() {
        return create(0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.VERTICAL, STD_INSETS);
    }

    /**
     * @return A fresh CENTER constraint.
     */
    public static GridBagConstraints center() {
        return create(1, 1, 1, 1, 1.0, 1.0, GridBagConstraints.BOTH, STD_INSETS);
    }

    /**
     * @return A fresh EAST constraint.
     */
    public static GridBagConstraints east() {
        return create(2, 1, 1, 1, 0.0, 1.0, GridBagConstraints.VERTICAL, STD_INSETS);
    }

    /**
     * @return A fresh SOUTH constraint.
     */
    public static GridBagConstraints south() {
        return create(0, 2, 3, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL, STD_INSETS);
    }

    /**
     * Fluent factory for specific spacing requirements. Usage: panel.add(comp,
     * GBCompass.north(10, 10, 10, 10));
     */
    public static GridBagConstraints north(int t, int l, int b, int r) {
        return copy(north(), t, l, b, r);
    }

    public static GridBagConstraints west(int t, int l, int b, int r) {
        return copy(west(), t, l, b, r);
    }

    public static GridBagConstraints center(int t, int l, int b, int r) {
        return copy(center(), t, l, b, r);
    }

    public static GridBagConstraints east(int t, int l, int b, int r) {
        return copy(east(), t, l, b, r);
    }

    public static GridBagConstraints south(int t, int l, int b, int r) {
        return copy(south(), t, l, b, r);
    }

    private static GridBagConstraints copy(GridBagConstraints target, int t, int l, int b, int r) {
        target.insets = new Insets(t, l, b, r);
        return target;
    }

    private static GridBagConstraints create(int x, int y, int w, int h, double wx, double wy, int fill, Insets i) {
        return new GridBagConstraints(x, y, w, h, wx, wy, GridBagConstraints.CENTER, fill, i, 0, 0);
    }
}
