package nl.infcomtec.infimg.pixelmicroscope;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

public class PixelMicroscopeFrame extends AFrame {

    /**
     * Fraction of the current screen's width given to the neighbourhood +
     * info column when no remembered width is on disk. The rest goes to the
     * big image — the actual point of the tool, so it should get the room,
     * not fight a user-dragged splitter for it every session.
     */
    private static final double DEFAULT_SIDE_FRACTION = 0.28;

    /**
     * Remembered window bounds + side-column width, read/written through a
     * caller-supplied {@link BoundsPersistence} rather than a private config
     * file — when embedded in another app (e.g. infimg), the host already has
     * its own config file and window-slot machinery; this avoids a second,
     * redundant one. {@link #main} supplies a minimal standalone
     * implementation for when this frame is run on its own.
     */
    public interface BoundsPersistence {

        /** Returns remembered bounds, or {@code null} if none saved yet. */
        Rectangle load();

        void save(Rectangle bounds, int sideWidth);

        /** Side-column width from the last {@link #save}, or -1 if none saved yet. */
        int loadSideWidth();
    }

    private final BoundsPersistence persistence;

    public final BigImagePanel bigImage;
    private final NeighborhoodPanel neighborhood;
    private final PixelInfoPanel info;
    private final APanel sidePanel;

    public PixelMicroscopeFrame(BoundsPersistence persistence) {
        super("Pixel Microscope");
        this.persistence = persistence;
        withExitOnClose();

        info = new PixelInfoPanel();
        neighborhood = new NeighborhoodPanel();
        bigImage = new BigImagePanel(neighborhood, info);
        neighborhood.setNavigator(bigImage);

        sidePanel = new APanel();
        sidePanel.add(neighborhood, GBCompass.north());
        GridBagConstraints infoConstraints = GBCompass.south();
        infoConstraints.weighty = 1.0;
        infoConstraints.fill = GridBagConstraints.BOTH;
        sidePanel.add(info, infoConstraints);

        getPanel().add(bigImage, GBCompass.center());
        getPanel().add(sidePanel, GBCompass.east());

        JMenuBar mb = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem open = new JMenuItem("Open...");
        open.addActionListener(new OpenAction());
        fileMenu.add(open);
        mb.add(fileMenu);
        setJMenuBar(mb);

        int sideWidth;
        Rectangle savedBounds = persistence.load();
        if (null != savedBounds && savedBounds.width > 0 && savedBounds.height > 0) {
            withBounds(savedBounds);
            int savedSideWidth = persistence.loadSideWidth();
            sideWidth = (savedSideWidth > 0) ? Math.max(280, savedSideWidth) : 280;
        } else {
            Rectangle screen = usableScreenBounds();
            withBounds(screen);
            sideWidth = (int) (screen.width * DEFAULT_SIDE_FRACTION);
        }
        neighborhood.setPreferredSize(new Dimension(sideWidth, sideWidth));
        info.setPreferredSize(new Dimension(sideWidth, info.getPreferredSize().height));

        addComponentListener(new BoundsSaver());
    }

    /**
     * The usable bounds (screen size minus OS taskbars/docks) of the display
     * this frame is being created on.
     */
    private static Rectangle usableScreenBounds() {
        GraphicsConfiguration gc = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom);
    }

    private final class BoundsSaver extends ComponentAdapter {

        @Override
        public void componentMoved(ComponentEvent e) {
            persistence.save(getBounds(), sidePanel.getWidth());
        }

        @Override
        public void componentResized(ComponentEvent e) {
            persistence.save(getBounds(), sidePanel.getWidth());
        }
    }

    private final class OpenAction implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser jfc = new JFileChooser();
            if (JFileChooser.APPROVE_OPTION != jfc.showOpenDialog(PixelMicroscopeFrame.this)) {
                return;
            }
            File f = jfc.getSelectedFile();
            try {
                bigImage.loadImage(f);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(PixelMicroscopeFrame.this,
                        "Failed to load " + f + ": " + ex,
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Bare JSON-file-backed {@link BoundsPersistence} for running this frame
     * standalone (via {@link #main}) rather than embedded in a host app that
     * supplies its own. Not used when launched from infimg's menu.
     */
    private static final class StandaloneBoundsPersistence implements BoundsPersistence {

        private static final File CONFIG_FILE = new File(System.getProperty("user.home"), ".pixelmicroscope.json");
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER
                = new com.fasterxml.jackson.databind.ObjectMapper();

        private int lastSideWidth = -1;

        @Override
        public Rectangle load() {
            if (!CONFIG_FILE.isFile()) {
                return null;
            }
            try {
                StandaloneConfig cfg = MAPPER.readValue(CONFIG_FILE, StandaloneConfig.class);
                lastSideWidth = cfg.sideWidth;
                return new Rectangle(cfg.x, cfg.y, cfg.width, cfg.height);
            } catch (IOException ex) {
                Logger.getLogger(PixelMicroscopeFrame.class.getName())
                        .log(Level.WARNING, "Could not read " + CONFIG_FILE, ex);
                return null;
            }
        }

        @Override
        public int loadSideWidth() {
            return lastSideWidth;
        }

        @Override
        public void save(Rectangle bounds, int sideWidth) {
            StandaloneConfig cfg = new StandaloneConfig();
            cfg.x = bounds.x;
            cfg.y = bounds.y;
            cfg.width = bounds.width;
            cfg.height = bounds.height;
            cfg.sideWidth = sideWidth;
            try {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE, cfg);
            } catch (IOException ex) {
                Logger.getLogger(PixelMicroscopeFrame.class.getName())
                        .log(Level.WARNING, "Could not save " + CONFIG_FILE, ex);
            }
        }
    }

    public static final class StandaloneConfig {

        public int x;
        public int y;
        public int width;
        public int height;
        public int sideWidth;
    }

    public static void main(String[] args) {
        File initial = (1 == args.length) ? new File(args[0]) : null;
        EventQueue.invokeLater(new StartupRunnable(initial));
    }

    private static final class StartupRunnable implements Runnable {

        private final File initial;

        StartupRunnable(File initial) {
            this.initial = initial;
        }

        @Override
        public void run() {
            PixelMicroscopeFrame f = new PixelMicroscopeFrame(new StandaloneBoundsPersistence());
            f.setMinimumSize(new Dimension(900, 600));
            f.showFrame();
            if (null != initial) {
                try {
                    f.bigImage.loadImage(initial);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(f,
                            "Failed to load " + initial + ": " + ex,
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
