/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.infimg.pixelmicroscope;

import java.awt.Rectangle;

/**
 *
 * Tiny interface for Swing things that moved were resized to tell interested
 * parties.
 */
public interface BoundsRecallCallback {

    void remember(Rectangle bounds);

}
