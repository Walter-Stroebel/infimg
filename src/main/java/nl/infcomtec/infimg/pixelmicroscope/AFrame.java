package nl.infcomtec.infimg.pixelmicroscope;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;

/**
 * Just a JFrame with some utility methods.
 * <p>
 * Trimmed for Pixel Microscope: the FlatLaf/NotePad demo {@code main} from the
 * catalog original was dropped since this copy is never run standalone.
 * </p>
 *
 * @author walter
 */
public class AFrame extends JFrame {

    private final APanel panel;

    public AFrame(String title) {
        super(title);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        panel = new APanel();
        setContentPane(panel);
    }

    /**
     * Adds a tab to the, now not optionally tabbed, container
     *
     * @param title Tab title.
     * @param ch Thing in the tab.
     */
    public void addTab(String title, Component ch) {
        getPanel().addTab(title, ch);
    }

    /**
     * Removes first matching tab.
     *
     * @param title Case insensitive.
     */
    public void delTab(String title) {
        getPanel().delTab(title);
    }

    /**
     * @return the tabPane
     */
    public JTabbedPane getTabPane() {
        return getPanel().getTabPane();
    }

    /**
     * @return the toolBar
     */
    public JToolBar getToolBar() {
        return getPanel().getToolBar();
    }

    public void showFrame() {
        if (getWidth() < 400 || getHeight() < 200) {
            // avoid zero-sized window
            setSize(400, 200);
        }
        if (EventQueue.isDispatchThread()) {
            super.setVisible(true);
        } else {
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    AFrame.super.setVisible(true);
                }
            });
        }
    }

    /**
     * Position on creation.
     *
     * @param w at least 60 (enforced)
     * @param h at least 40 (enforced)
     */
    public AFrame withBounds(int x, int y, int w, int h) {
        setBounds(x, y, Math.max(w, 60), Math.max(h, 40));
        return this;
    }

    public AFrame withBounds(Rectangle r) {
        setBounds(r.x, r.y, r.width, r.height);
        return this;
    }

    public AFrame withBoundsRecall(final BoundsRecallCallback brc) {

        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentMoved(ComponentEvent e) {
                brc.remember(getBounds());
            }

            @Override
            public void componentResized(ComponentEvent e) {
                brc.remember(getBounds());
            }
        });

        return this;
    }

    public AFrame withExitOnClose() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        return this;
    }

    /**
     * Make this a tabbed container, only use once.
     *
     * @param handler Optional tab-click handler.
     * @return for chaining.
     */
    public AFrame withTabs(TabSignals handler) {
        getPanel().withTabs(handler);
        return this;
    }

    public AFrame withToolBar(JComponent... cmp) {
        getPanel().withToolBar(cmp);
        validate();
        repaint();
        return this;
    }

    public AFrame withMenu(String menuName, AbstractAction... items) {
        JMenuBar menuBar = getJMenuBar();
        if (null == menuBar) {
            menuBar = new JMenuBar();
        }
        JMenu menu = new JMenu(menuName);
        if (null != items) {
            for (AbstractAction aa : items) {
                if (null == aa) {
                    menu.addSeparator();
                } else {
                    menu.add(aa);
                }
            }
        }
        menuBar.add(menu);
        if (null == getJMenuBar()) {
            setJMenuBar(menuBar);
            validate();
            repaint();
        }
        return this;
    }

    /**
     * @return the panel
     */
    public APanel getPanel() {
        return panel;
    }

}
