/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.infimg.pixelmicroscope;

import java.awt.Component;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * JPanel with optional tab and toolbar support.
 */
public class APanel extends JPanel {

    private JTabbedPane tabPane;
    private JToolBar toolBar;

    public APanel() {
        super(new GridBagLayout());
    }

    public APanel(SwingProps props) {
        super(new GridBagLayout());
        props.apply(this);
    }

    public APanel withProps(SwingProps props) {
        props.apply(this);
        return this;
    }

    /**
     * Adds a tab.
     *
     * @param title For the tab.
     * @param ch Component to populate the tab.
     */
    public synchronized void addTab(String title, Component ch) {
        if (null == getTabPane()) {
            add(tabPane = new JTabbedPane(), GBCompass.center());
        }
        getTabPane().add(title, ch);
    }

    /**
     * Removes first matching tab.
     */
    public void delTab(String title) {
        if (null == getTabPane()) {
            throw new RuntimeException("Not a tabbed container.");
        }
        for (int i = 0; i < getTabPane().getTabCount(); i++) {
            if (getTabPane().getTitleAt(i).equalsIgnoreCase(title)) {
                getTabPane().remove(i);
                return;
            }
        }
    }

    public JTabbedPane getTabPane() {
        return tabPane;
    }

    public JToolBar getToolBar() {
        return toolBar;
    }

    /**
     * Make this panel tabbed, only use once.
     */
    public APanel withTabs(TabSignals handler) {
        if (null != getTabPane()) {
            throw new RuntimeException("RTFM: once is once!");
        }

        add(tabPane = new JTabbedPane(), GBCompass.center());

        if (null != handler) {

            getTabPane().addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    handler.selectionChanged(getTabPane(), e);
                }
            });

            getTabPane().addMouseListener(new MouseAdapter() {

                @Override
                public void mouseReleased(MouseEvent e) {
                    int index = getTabPane().indexAtLocation(e.getX(), e.getY());
                    if (index < 0) {
                        return;
                    }

                    if (SwingUtilities.isLeftMouseButton(e)) {
                        handler.tabLeftClicked(getTabPane(), index);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        handler.tabRightClicked(getTabPane(), index);
                    }
                }
            });
        }

        return this;
    }

    /**
     * Adds a toolbar.
     * <p>
     * Can be called more than once.</p>
     *
     * @param cmp Buttons or fixed-sized fields advised!
     * @return Panel now with toolbar at the top.
     */
    public synchronized APanel withToolBar(Collection<JComponent> cmp) {
        boolean doAdd = false;

        if (null == toolBar) {
            toolBar = new JToolBar();
            doAdd = true;
        }
        if (null != cmp) {
            for (JComponent jc : cmp) {
                getToolBar().add(jc);
            }
        }
        if (doAdd) {
            add(getToolBar(), GBCompass.north());
        }
        return this;
    }

    /**
     * Adds a toolbar.
     * <p>
     * Can be called more than once.</p>
     *
     * @param cmp Buttons or fixed-sized fields advised!
     * @return Panel now with toolbar at the top.
     */
    public synchronized APanel withToolBar(JComponent... cmp) {
        boolean doAdd = false;

        if (null == toolBar) {
            toolBar = new JToolBar();
            doAdd = true;
        }
        if (null != cmp) {
            for (JComponent jc : cmp) {
                getToolBar().add(jc);
            }
        }
        if (doAdd) {
            add(getToolBar(), GBCompass.north());
        }
        return this;
    }
}
