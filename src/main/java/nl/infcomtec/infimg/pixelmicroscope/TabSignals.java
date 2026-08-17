/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.infimg.pixelmicroscope;

import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;

/**
 *
 * Tiny interface for Tabbed Panes to communicate.
 */
public interface TabSignals {

    public void selectionChanged(JTabbedPane jtp, ChangeEvent e);

    public void tabLeftClicked(JTabbedPane jtp, int index);

    public void tabRightClicked(JTabbedPane jtp, int index);

}
