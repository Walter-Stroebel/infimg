/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.infimg;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/**
 * Standalone, OS-agnostic image viewer: fit-to-window load (rescaled up or
 * down to exactly fill whichever window size is current when a file loads,
 * not a fixed default), mouse-wheel zoom (or, with the toolbar's toggle
 * down, mouse-wheel rotate through an arbitrary angle, not just 90-degree
 * steps), and click-drag pan. Save writes exactly the current on-screen
 * pixels, zoom/rotation/pan/crop included, not a re-render of the original
 * at full resolution.
 * <p>
 * A single self-contained file, deliberately: no dependency on anything but
 * Jackson's databind module (used only to read/write the tiny window-bounds
 * config below). An optional first CLI argument opens that file on launch.
 * </p>
 * <p>
 * The title bar always names what's on screen — the loaded file's bare name
 * ({@link #load}), never a full path (callers, e.g. Voynich's
 * {@code CatalogCli extract --view}, often pass long {@code /tmp} paths that
 * would be unreadable truncated in a title bar), or, for a clipboard paste
 * with no source file at all ({@link #pasteFromClipboard}), {@code "(clip)"}
 * plus a millisecond timestamp — needed to tell apart multiple clipboard
 * grabs open side by side, which would otherwise all read "ImageView".
 * </p>
 */
public final class ImageView extends JFrame {

    private static final File CONFIG_FILE = new File(System.getProperty("user.home"), ".infimg.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ImageCanvas canvas = new ImageCanvas();

    public static void main(String[] args) {
        int slot = 0;
        File fileArg = null;
        for (String arg : args) {
            if (arg.length() == 2 && arg.charAt(0) == '-' && Character.isDigit(arg.charAt(1))) {
                slot = arg.charAt(1) - '0';
            } else {
                fileArg = new File(arg);
            }
        }
        final int startSlot = slot;
        final File toLoad = fileArg;
        applyLookAndFeel(readStoredLaf());
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ImageView view = new ImageView(startSlot);
                view.setVisible(true);
                if (null != toLoad) {
                    view.load(toLoad);
                }
            }
        });
    }

    /** Look-and-feel names {@link #applyLookAndFeel} understands; also what Menu → Look & Feel lists. */
    private static final String[] LAF_NAMES = {"System Default", "FlatLaf Light", "FlatLaf Dark", "FlatLaf IntelliJ", "FlatLaf Darcula"};

    private static String readStoredLaf() {
        if (!CONFIG_FILE.isFile()) {
            return LAF_NAMES[0];
        }
        try {
            AppConfig cfg = MAPPER.readValue(CONFIG_FILE, AppConfig.class);
            return null == cfg.laf ? LAF_NAMES[0] : cfg.laf;
        } catch (IOException ex) {
            return LAF_NAMES[0];
        }
    }

    /** Installs the named look-and-feel (see {@link #LAF_NAMES}); must run before any Swing component is created. */
    private static void applyLookAndFeel(String name) {
        try {
            switch (name) {
                case "FlatLaf Light":
                    javax.swing.UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
                    break;
                case "FlatLaf Dark":
                    javax.swing.UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
                    break;
                case "FlatLaf IntelliJ":
                    javax.swing.UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatIntelliJLaf());
                    break;
                case "FlatLaf Darcula":
                    javax.swing.UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarculaLaf());
                    break;
                default:
                    javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            Logger.getLogger(ImageView.class.getName()).log(Level.WARNING, "Could not apply look and feel " + name, ex);
        }
    }

    /** Which of the 10 remembered window-position slots this instance tracks on move/resize. */
    private int activeSlot;

    public ImageView(int startSlot) {
        super("ImageView");
        this.activeSlot = startSlot;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton loadButton = new JButton("Load");
        loadButton.setToolTipText("Open an image file");
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(ImageView.this)) {
                    load(chooser.getSelectedFile());
                }
            }
        });
        toolBar.add(loadButton);

        JButton saveButton = new JButton("Save");
        saveButton.setToolTipText("Save exactly the current view (zoom, rotation, pan) to a new image file");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
            }
        });
        toolBar.add(saveButton);

        JButton pasteButton = new JButton("Paste");
        pasteButton.setToolTipText("Load the image currently on the system clipboard");
        pasteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pasteFromClipboard();
            }
        });
        toolBar.add(pasteButton);

        JButton copyButton = new JButton("Copy");
        copyButton.setToolTipText("Copy exactly the current view (zoom, rotation, pan) to the system clipboard");
        copyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyToClipboard();
            }
        });
        toolBar.add(copyButton);
        toolBar.addSeparator();

        final JToggleButton rotateToggle = new JToggleButton("Rotate (wheel)");
        rotateToggle.setToolTipText("When down, the mouse wheel rotates the image instead of zooming it");
        rotateToggle.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.rotateOnWheel = rotateToggle.isSelected();
            }
        });
        toolBar.add(rotateToggle);

        JButton fitButton = new JButton("Fit");
        fitButton.setToolTipText("Rescale and re-center to fill the window as it is now"
                + " — for after a manual resize or a wandered-off zoom/pan");
        fitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.fitToWindow();
            }
        });
        toolBar.add(fitButton);

        toolBar.add(Box.createHorizontalGlue());

        final JButton menuButton = new JButton("Menu");
        menuButton.setToolTipText("More features, tucked away so the main toolbar stays simple");
        menuButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                buildMenu().show(menuButton, 0, menuButton.getHeight());
            }
        });
        toolBar.add(menuButton);

        JButton exitButton = new JButton("Exit");
        exitButton.setToolTipText("Close this viewer — no unsaved-changes prompt, use Save first if you want this exact view kept");
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        toolBar.add(exitButton);

        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        AppConfig cfg = loadConfig();
        ViewConfig slotCfg = cfg.slots[activeSlot];
        if (null != slotCfg && slotCfg.width > 0 && slotCfg.height > 0) {
            setBounds(slotCfg.x, slotCfg.y, slotCfg.width, slotCfg.height);
        } else {
            Dimension screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds().getSize();
            setSize(Math.min(1200, screen.width), Math.min(900, screen.height));
            setLocationRelativeTo(null);
        }
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                saveIntoSlot(activeSlot);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                saveIntoSlot(activeSlot);
            }
        });
    }

    /** Builds the [Menu] popup fresh each click, so slot labels always reflect the file on disk. */
    private JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();
        AppConfig cfg = loadConfig();

        JMenu loadMenu = new JMenu("Load Slot");
        JMenu saveMenu = new JMenu("Save as Slot");
        for (int i = 0; i < 10; i++) {
            final int slot = i;
            ViewConfig slotCfg = cfg.slots[slot];
            String label = slot + (null == slotCfg ? " (empty)"
                    : String.format(" (%dx%d @ %d,%d)", slotCfg.width, slotCfg.height, slotCfg.x, slotCfg.y));

            JMenuItem loadItem = new JMenuItem(label);
            loadItem.setEnabled(null != slotCfg);
            loadItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    loadSlot(slot);
                }
            });
            loadMenu.add(loadItem);

            JMenuItem saveItem = new JMenuItem(label);
            saveItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    saveAsSlot(slot);
                }
            });
            saveMenu.add(saveItem);
        }
        menu.add(loadMenu);
        menu.add(saveMenu);

        menu.addSeparator();
        JMenuItem metadataItem = new JMenuItem("Metadata (ImageMagick identify)");
        metadataItem.setEnabled(cfg.imageMagick && null != currentFile);
        metadataItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showMetadata();
            }
        });
        menu.add(metadataItem);

        boolean hasImage = null != canvas.source;
        menu.add(adjustMenuItem("Lighter", hasImage, +BRIGHTNESS_STEP));
        menu.add(adjustMenuItem("Darker", hasImage, -BRIGHTNESS_STEP));
        JMenuItem moreContrast = new JMenuItem("More Contrast");
        moreContrast.setEnabled(hasImage);
        moreContrast.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyAdjustmentAsync(ImageView::adjustContrast, +CONTRAST_STEP);
            }
        });
        menu.add(moreContrast);
        JMenuItem lessContrast = new JMenuItem("Less Contrast");
        lessContrast.setEnabled(hasImage);
        lessContrast.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyAdjustmentAsync(ImageView::adjustContrast, -CONTRAST_STEP);
            }
        });
        menu.add(lessContrast);

        menu.addSeparator();
        JMenu lafMenu = new JMenu("Look & Feel");
        String currentLaf = null == cfg.laf ? LAF_NAMES[0] : cfg.laf;
        for (final String name : LAF_NAMES) {
            JMenuItem lafItem = new JMenuItem(name);
            lafItem.setEnabled(!name.equals(currentLaf));
            lafItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    setLookAndFeel(name);
                }
            });
            lafMenu.add(lafItem);
        }
        menu.add(lafMenu);

        return menu;
    }

    /**
     * Applies {@code name} to the running app immediately (via
     * {@link SwingUtilities#updateComponentTreeUI}, so no restart is
     * needed to preview it) and persists it to {@code ~/.infimg.json} so
     * future launches start with it already installed — {@link #main}
     * applies the stored LAF before any Swing component exists, which
     * this live in-session switch can't retroactively do for windows
     * that were already built with the old one otherwise.
     */
    private void setLookAndFeel(String name) {
        applyLookAndFeel(name);
        SwingUtilities.updateComponentTreeUI(this);
        AppConfig cfg = loadConfig();
        cfg.laf = name;
        writeConfig(cfg);
    }

    /** Jumps this window to slot's stored geometry and starts tracking that slot on move/resize. */
    private void loadSlot(int slot) {
        AppConfig cfg = loadConfig();
        ViewConfig slotCfg = cfg.slots[slot];
        if (null == slotCfg) {
            return;
        }
        activeSlot = slot;
        setBounds(slotCfg.x, slotCfg.y, slotCfg.width, slotCfg.height);
    }

    /**
     * Runs {@code identify -verbose} (ImageMagick) on {@link #currentFile}
     * and shows the raw output in a scrollable dialog — unparsed, so it
     * works with whatever ImageMagick version is on the user's PATH.
     * Gated in {@link #buildMenu} on the {@code imageMagick} config flag
     * (the user's own attestation that it's installed, never auto-probed)
     * and on having an actual file on disk (a clipboard paste has none).
     */
    private void showMetadata() {
        try {
            Process proc = new ProcessBuilder("identify", "-verbose", currentFile.getAbsolutePath())
                    .redirectErrorStream(true).start();
            String output = new String(proc.getInputStream().readAllBytes());
            proc.waitFor();
            javax.swing.JTextArea textArea = new javax.swing.JTextArea(output, 40, 100);
            textArea.setEditable(false);
            textArea.setCaretPosition(0);
            JOptionPane.showMessageDialog(this, new javax.swing.JScrollPane(textArea),
                    "Metadata — " + currentFile.getName(), JOptionPane.PLAIN_MESSAGE);
        } catch (IOException | InterruptedException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Metadata failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** L* points moved per Lighter/Darker click — small and repeatable, a nudge rather than a dial. */
    private static final int BRIGHTNESS_STEP = 5;
    /** Sigmoid steepness delta per More/Less Contrast click. */
    private static final double CONTRAST_STEP = 0.15;

    /** Builds a single Lighter/Darker Menu item that applies a fixed L* step immediately, no dialog. */
    private JMenuItem adjustMenuItem(String label, boolean enabled, final int deltaL) {
        JMenuItem item = new JMenuItem(label);
        item.setEnabled(enabled);
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyAdjustmentAsync(ImageView::adjustBrightness, deltaL);
            }
        });
        return item;
    }

    /** A per-pixel CIELAB adjustment: takes the current L*, a*, b* and a step amount, mutates them in place. */
    private interface LabStep {

        void apply(double[] lab, double step);
    }

    /**
     * Runs {@code op} against {@link ImageCanvas#source} off the EDT (full
     * sRGB↔XYZ↔Lab round trips per pixel, parallelized across all cores in
     * {@link #mapPerPixelLab}, are real work even so) and swaps the result
     * into the canvas — replacing it in place, not resetting, so repeated
     * clicks (Lighter, Lighter, More Contrast, ...) compound on the
     * current view rather than each starting over from the loaded file.
     */
    private void applyAdjustmentAsync(final LabStep op, final double step) {
        final BufferedImage source = canvas.source;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final BufferedImage adjusted = mapPerPixelLab(source, op, step);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        canvas.replaceImageKeepingView(adjusted);
                    }
                });
            }
        }, "infimg-adjust").start();
    }

    /** Adds {@code step} (an L* offset in points) to L*, clamped 0..100 — the "Lighter"/"Darker" operation. */
    private static void adjustBrightness(double[] lab, double step) {
        lab[0] = Math.max(0, Math.min(100, lab[0] + step));
    }

    /**
     * Pushes L* through a logistic S-curve centered on middle grey
     * (L*=50), steepened or flattened by {@code step} — the "More/Less
     * Contrast" operation. Unlike a naive RGB contrast stretch, this
     * spreads/compresses perceptual lightness around a fixed pivot rather
     * than raw channel values, so color balance doesn't shift as a side
     * effect of the contrast change.
     */
    private static void adjustContrast(double[] lab, double step) {
        double normalized = (lab[0] - 50) / 50;
        double k = Math.max(0.05, 1.0 + step);
        double sigmoid = 1.0 / (1.0 + Math.exp(-k * normalized * 3)) * 2 - 1;
        lab[0] = Math.max(0, Math.min(100, 50 + sigmoid * 50));
    }

    /**
     * Applies {@code op} to every pixel's CIELAB L*, a*, b*, converts back
     * to sRGB, preserving alpha. Parallelized by row across all available
     * cores — on modern multi-core hardware that's worth spending rather
     * than falling back to naive per-channel RGB math like
     * {@link Color#brighter} / {@link Color#darker} do.
     */
    private static BufferedImage mapPerPixelLab(BufferedImage src, final LabStep op, final double step) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        final int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);
        final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final int[] result = new int[pixels.length];
        int cores = Runtime.getRuntime().availableProcessors();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(cores);
        java.util.List<java.util.concurrent.Future<?>> tasks = new java.util.ArrayList<>();
        int rowsPerTask = Math.max(1, h / cores);
        for (int startRow = 0; startRow < h; startRow += rowsPerTask) {
            final int from = startRow;
            final int to = Math.min(h, startRow + rowsPerTask);
            tasks.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    double[] lab = new double[3];
                    for (int y = from; y < to; y++) {
                        int rowOffset = y * w;
                        for (int x = 0; x < w; x++) {
                            int argb = pixels[rowOffset + x];
                            int alpha = argb >>> 24;
                            EnhancedColor.getCIELAB(argb, lab);
                            op.apply(lab, step);
                            EnhancedColor adjusted = EnhancedColor.fromCIELAB(lab[0], lab[1], lab[2]);
                            result[rowOffset + x] = (alpha << 24) | (adjusted.getRGB() & 0xFFFFFF);
                        }
                    }
                }
            }));
        }
        for (java.util.concurrent.Future<?> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException | java.util.concurrent.ExecutionException ex) {
                Thread.currentThread().interrupt();
                Logger.getLogger(ImageView.class.getName()).log(Level.WARNING, "Adjustment task failed", ex);
            }
        }
        pool.shutdown();
        out.setRGB(0, 0, w, h, result, 0, w);
        return out;
    }

    /**
     * Promotes the current on-screen geometry into {@code slot} and switches
     * live tracking to it. If this session started on slot 0 and is being
     * promoted away from it, slot 0 is reverted to whatever it held before
     * this session began, undoing any autosave drift picked up along the
     * way — so the original slot-0 position survives even though this
     * window kept overwriting it while it was still the active slot.
     */
    private void saveAsSlot(int slot) {
        AppConfig cfg = loadConfig();
        if (0 == activeSlot && slot != activeSlot) {
            cfg.slots[0] = preSessionSlotZero;
        }
        cfg.slots[slot] = boundsToConfig();
        writeConfig(cfg);
        activeSlot = slot;
    }

    private ViewConfig boundsToConfig() {
        Rectangle b = getBounds();
        ViewConfig cfg = new ViewConfig();
        cfg.x = b.x;
        cfg.y = b.y;
        cfg.width = b.width;
        cfg.height = b.height;
        return cfg;
    }

    private void saveIntoSlot(int slot) {
        AppConfig cfg = loadConfig();
        cfg.slots[slot] = boundsToConfig();
        writeConfig(cfg);
    }

    private static void writeConfig(AppConfig cfg) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE, cfg);
        } catch (IOException ex) {
            Logger.getLogger(ImageView.class.getName()).log(Level.WARNING, "Could not save " + CONFIG_FILE, ex);
        }
    }

    /** Slot 0's geometry as it was when this session started (possibly null, i.e. empty), so {@link #saveAsSlot} can restore it. */
    private ViewConfig preSessionSlotZero;
    private boolean preSessionSlotZeroCaptured;

    private AppConfig loadConfig() {
        AppConfig cfg = new AppConfig();
        if (CONFIG_FILE.isFile()) {
            try {
                AppConfig read = MAPPER.readValue(CONFIG_FILE, AppConfig.class);
                System.arraycopy(read.slots, 0, cfg.slots, 0, Math.min(read.slots.length, 10));
                cfg.imageMagick = read.imageMagick;
                cfg.laf = read.laf;
            } catch (IOException ex) {
                Logger.getLogger(ImageView.class.getName()).log(Level.WARNING, "Could not read " + CONFIG_FILE, ex);
            }
        }
        if (!preSessionSlotZeroCaptured) {
            preSessionSlotZero = cfg.slots[0];
            preSessionSlotZeroCaptured = true;
        }
        return cfg;
    }

    /** Plain POJO mirroring {@code ~/.infimg.json}: the 10 window-position slots plus feature flags for optional external tools. */
    public static final class AppConfig {

        public ViewConfig[] slots = new ViewConfig[10];
        /** User's own attestation that ImageMagick's {@code identify} is on PATH — never auto-probed, see MANUAL.md. */
        public boolean imageMagick = false;
        /** One of {@link #LAF_NAMES}; applied at startup before any Swing component is created. Null/unset means System Default. */
        public String laf = "System Default";
    }

    /** Plain POJO mirroring one slot of {@code ~/.infimg.json}'s last on-screen window bounds. */
    public static final class ViewConfig {

        public int x;
        public int y;
        public int width;
        public int height;
    }

    /** Backing file of what's currently loaded, or null (e.g. after {@link #pasteFromClipboard}) — needed by menu features like Metadata that shell out to a file-based external tool. */
    private File currentFile;

    private void load(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (null == img) {
                JOptionPane.showMessageDialog(this, "Not a readable image: " + file, "Load failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            canvas.setImage(img);
            currentFile = file;
            setTitle(file.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Load failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save() {
        if (null == canvas.source) {
            JOptionPane.showMessageDialog(this, "No image loaded", "Save failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (JFileChooser.APPROVE_OPTION != chooser.showSaveDialog(this)) {
            return;
        }
        File file = chooser.getSelectedFile();
        String name = file.getName();
        String format = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "png";
        try {
            ImageIO.write(renderCurrentView(), format, file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Renders exactly the current on-screen pixels of {@link #canvas} —
     * same content {@link #save} writes to disk. {@code TYPE_INT_RGB}, not
     * {@code _ARGB}: {@link ImageCanvas#paintComponent} always fills the
     * background opaque before drawing, so no pixel is ever actually
     * transparent — and on Linux/X11, copying an {@code _ARGB} image to
     * the system clipboard hits a JDK bug where the X clipboard manager's
     * PNG round-trip (used to keep the data available after this process
     * exits) can't be read back via {@code imageFlavor}, failing with
     * "Error reading PNG image data" on the very next paste.
     */
    private BufferedImage renderCurrentView() {
        BufferedImage out = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        canvas.paint(g);
        g.dispose();
        return out;
    }

    private void pasteFromClipboard() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable content = clipboard.getContents(null);
        if (null == content || !content.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            JOptionPane.showMessageDialog(this, "No image on the clipboard", "Paste failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            java.awt.Image img = (java.awt.Image) content.getTransferData(DataFlavor.imageFlavor);
            BufferedImage buffered = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = buffered.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            canvas.setImage(buffered);
            currentFile = null;
            setTitle(String.format("(clip) %tH:%<tM:%<tS.%<tL", System.currentTimeMillis()));
        } catch (UnsupportedFlavorException | IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Paste failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copyToClipboard() {
        if (null == canvas.source) {
            JOptionPane.showMessageDialog(this, "No image loaded", "Copy failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        final BufferedImage view = renderCurrentView();
        Transferable transferable = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.imageFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.imageFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!DataFlavor.imageFlavor.equals(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return view;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
    }

    /**
     * Draws {@link #source} under a single affine transform combining zoom,
     * rotation, and pan. The rotation/zoom pivot is always the panel's true
     * on-screen center — {@link #panX}/{@link #panY} are stored in
     * image-space units (unrotated, unscaled), not screen pixels, so that
     * dragging never shifts that pivot the way accumulating a screen-space
     * pan offset into the same translate as the pivot used to (wheel
     * rotation/zoom then swung wildly around whatever off-screen point the
     * pan had wandered the image center to). Point order
     * (last-applied-first, per {@link AffineTransform}'s concatenation
     * semantics): the image is first centered on the origin and shifted by
     * the image-space pan, then scaled, then rotated, then translated to
     * the fixed panel center.
     */
    private static final class ImageCanvas extends JComponent {

        private BufferedImage source;
        private double zoom = 1.0;
        private double rotationDeg = 0.0;
        private double panX = 0.0;
        private double panY = 0.0;
        private boolean rotateOnWheel = false;
        private int dragLastX;
        private int dragLastY;

        ImageCanvas() {
            setBackground(Color.DARK_GRAY);
            setOpaque(true);
            addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (null == source) {
                        return;
                    }
                    int notches = e.getWheelRotation();
                    if (rotateOnWheel) {
                        rotationDeg += notches * 2.0;
                    } else {
                        zoom *= Math.pow(1.1, -notches);
                    }
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragLastX = e.getX();
                    dragLastY = e.getY();
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    int dx = e.getX() - dragLastX;
                    int dy = e.getY() - dragLastY;
                    double rad = -Math.toRadians(rotationDeg);
                    double cos = Math.cos(rad);
                    double sin = Math.sin(rad);
                    panX += (dx * cos - dy * sin) / zoom;
                    panY += (dx * sin + dy * cos) / zoom;
                    dragLastX = e.getX();
                    dragLastY = e.getY();
                    repaint();
                }
            });
        }

        void setImage(BufferedImage img) {
            source = img;
            rotationDeg = 0.0;
            fitToWindow();
        }

        /** Swaps {@link #source} in place — unlike {@link #setImage}, keeps zoom/rotation/pan untouched, for pixel-adjustment previews (e.g. brightness) that shouldn't reset the user's current view. */
        void replaceImageKeepingView(BufferedImage img) {
            source = img;
            repaint();
        }

        /**
         * Rescales (up or down) and re-centers {@link #source} to exactly
         * fill the panel's current size — the same math {@link #setImage}
         * applies on load, exposed separately for the toolbar's "Fit"
         * button, for after the user has manually resized the window or
         * wandered off with zoom/pan.
         */
        void fitToWindow() {
            panX = 0.0;
            panY = 0.0;
            if (null == source) {
                return;
            }
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            zoom = Math.min((double) w / source.getWidth(), (double) h / source.getHeight());
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (null == source) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            AffineTransform at = new AffineTransform();
            at.translate(getWidth() / 2.0, getHeight() / 2.0);
            at.rotate(Math.toRadians(rotationDeg));
            at.scale(zoom, zoom);
            at.translate(-source.getWidth() / 2.0 + panX, -source.getHeight() / 2.0 + panY);
            g2.drawImage(source, at, null);
        }
    }
}
