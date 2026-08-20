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
import javax.swing.SwingWorker;

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

    /** Where window slots / imageMagick flag / look-and-feel are persisted — under MITSA's shared app-data root ({@code nl.infcomtec.mitsa.MitsaPaths#appDataDir}) unless {@code -c PATH}/{@code --config-file PATH} names another file, e.g. so two independently-launched instances don't share one slot set. Set once by {@link #main} before any Swing component (and so before any config read/write) exists; never reassigned after. */
    private static File CONFIG_FILE = new File(nl.infcomtec.mitsa.MitsaPaths.appDataDir("infimg"), "infimg.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ImageCanvas canvas = new ImageCanvas();

    /**
     * One CLI-requested edit, applied once to the first file shown, in the
     * order given on the command line — see {@link #main}'s {@code --rotate}/
     * {@code --flip-hor}/{@code --flip-ver}/{@code --lighter}/{@code --darker}/
     * {@code --more-contrast}/{@code --less-contrast} parsing. Deliberately
     * not reapplied on {@link #loadNext}/{@link #loadPrevious}: infimg is a
     * viewer, not a batch image processor (see CLAUDE.md's Feature scope
     * section) — these flags are a scriptable stand-in for a few Menu
     * clicks on the file you're about to look at, nothing more.
     */
    private interface CliOp {

        void apply(ImageView view);
    }

    /** Printed for {@code --help}/{@code -h} and on an unrecognized {@code --}-prefixed option. */
    private static void printUsage() {
        System.out.println("Usage: infimg [-0..-9] [--slot N] [-c|--config-file FILE] [--rotate DEGREES]");
        System.out.println("              [--flip-hor] [--flip-ver] [--lighter] [--darker]");
        System.out.println("              [--more-contrast] [--less-contrast] [FILE...]");
    }

    public static void main(String[] args) {
        int slot = 0;
        final java.util.List<File> files = new java.util.ArrayList<>();
        final java.util.List<CliOp> ops = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.length() == 2 && arg.charAt(0) == '-' && Character.isDigit(arg.charAt(1))) {
                slot = arg.charAt(1) - '0';
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsage();
                System.exit(0);
            } else if ("--slot".equals(arg)) {
                slot = Integer.parseInt(args[++i]);
            } else if ("--config-file".equals(arg) || "-c".equals(arg)) {
                CONFIG_FILE = new File(args[++i]);
            } else if ("--rotate".equals(arg)) {
                final double degrees = Double.parseDouble(args[++i]);
                if (degrees < 0 || degrees >= 360) {
                    System.err.println("--rotate must be 0..359, got " + degrees);
                    System.exit(1);
                }
                ops.add(new CliOp() {
                    @Override
                    public void apply(ImageView view) {
                        view.canvas.rotateTo(degrees);
                    }
                });
            } else if ("--flip-hor".equals(arg)) {
                ops.add(new CliOp() {
                    @Override
                    public void apply(ImageView view) {
                        view.canvas.toggleFlipHorizontal();
                    }
                });
            } else if ("--flip-ver".equals(arg)) {
                ops.add(new CliOp() {
                    @Override
                    public void apply(ImageView view) {
                        view.canvas.toggleFlipVertical();
                    }
                });
            } else if ("--lighter".equals(arg)) {
                ops.add(new CliOp() {
                    @Override
                    public void apply(ImageView view) {
                        view.applyAdjustmentAsync(new BrightnessStep(), +BRIGHTNESS_STEP);
                    }
                });
            } else if ("--darker".equals(arg)) {
                ops.add(new CliOp() {
                    @Override
                    public void apply(ImageView view) {
                        view.applyAdjustmentAsync(new BrightnessStep(), -BRIGHTNESS_STEP);
                    }
                });
            } else if ("--more-contrast".equals(arg)) {
                ops.add(new CliOp() {
                    @Override
                    public void apply(ImageView view) {
                        view.applyAdjustmentAsync(new ContrastStep(), +CONTRAST_STEP);
                    }
                });
            } else if ("--less-contrast".equals(arg)) {
                ops.add(new CliOp() {
                    @Override
                    public void apply(ImageView view) {
                        view.applyAdjustmentAsync(new ContrastStep(), -CONTRAST_STEP);
                    }
                });
            } else if (arg.startsWith("--")) {
                System.err.println("Unrecognized option: " + arg);
                printUsage();
                System.exit(1);
            } else {
                files.add(new File(arg));
            }
        }
        final int startSlot = slot;
        applyLookAndFeel(readStoredLaf());
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ImageView view = new ImageView(startSlot);
                view.setVisible(true);
                if (!files.isEmpty()) {
                    view.fileList = files;
                    view.fileIndex = 0;
                    view.updateNavButtons();
                    view.load(files.get(0), ops);
                }
            }
        });
    }

    /** Look-and-feel names {@link #applyLookAndFeel} understands; also what Menu → Look & Feel lists. */
    private static final String[] LAF_NAMES = {"System Default", "FlatLaf Light", "FlatLaf Dark", "FlatLaf IntelliJ", "FlatLaf Darcula"};

    /**
     * The look-and-feel a brand-new {@code infimg.json} (MITSA app-data dir) (or one
     * missing the {@code laf} field) starts on — never overrides an
     * explicit prior choice, only fills in when there isn't one yet.
     * "System Default" is a genuinely good choice on macOS (Aqua) and
     * Windows, but on Linux it resolves to GTK's Swing LAF, whose
     * {@code JFileChooser} in particular looks dated/inconsistent (flagged
     * 2026-08-13) — so Linux gets FlatLaf Darcula instead, the other
     * platforms keep their native look.
     */
    private static String defaultLaf() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            return "FlatLaf Darcula";
        }
        return LAF_NAMES[0];
    }

    private static String readStoredLaf() {
        if (!CONFIG_FILE.isFile()) {
            return defaultLaf();
        }
        try {
            AppConfig cfg = MAPPER.readValue(CONFIG_FILE, AppConfig.class);
            return null == cfg.laf ? defaultLaf() : cfg.laf;
        } catch (IOException ex) {
            return defaultLaf();
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

    /** Steps to the previous file in {@link #fileList} — enabled only past the first file. */
    private JButton prevButton;

    /** Steps to the next file in {@link #fileList} — enabled only before the last file. */
    private JButton nextButton;

    /** Enables/disables {@link #prevButton}/{@link #nextButton} per {@link #fileIndex}'s position in {@link #fileList} — called after {@link #fileList} changes and after every {@link #loadNext}/{@link #loadPrevious}. */
    private void updateNavButtons() {
        prevButton.setEnabled(fileIndex > 0);
        nextButton.setEnabled(fileIndex + 1 < fileList.size());
    }

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
        toolBar.add(javax.swing.Box.createHorizontalStrut(4));

        JButton recentButton = new JButton("▾");
        recentButton.setToolTipText("Recently opened files");
        recentButton.setMargin(new java.awt.Insets(2, 8, 2, 8));
        recentButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showRecentFilesMenu(recentButton);
            }
        });
        toolBar.add(recentButton);

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

        final javax.swing.JLabel rotationLabel = new javax.swing.JLabel("0°");
        rotationLabel.setToolTipText("Current rotation");
        rotationLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 6, 0, 6));
        canvas.rotationListener = new RotationListener() {
            @Override
            public void rotationChanged(double degrees) {
                double normalized = degrees % 360.0;
                if (normalized < 0) {
                    normalized += 360.0;
                }
                rotationLabel.setText(Math.round(normalized) + "°");
            }
        };
        toolBar.add(rotationLabel);

        prevButton = new JButton("Prev");
        prevButton.setToolTipText("Load the previous file from the command-line file list");
        prevButton.setEnabled(false);
        prevButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadPrevious();
                updateNavButtons();
            }
        });
        toolBar.add(prevButton);

        nextButton = new JButton("Next");
        nextButton.setToolTipText("Load the next file from the command-line file list");
        nextButton.setEnabled(false);
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadNext();
                updateNavButtons();
            }
        });
        toolBar.add(nextButton);

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
        boolean hasImage = null != canvas.source;
        JMenuItem metadataItem = new JMenuItem(cfg.imageMagick ? "Metadata (ImageMagick identify)" : "Metadata");
        metadataItem.setEnabled(hasImage);
        metadataItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showMetadata();
            }
        });
        menu.add(metadataItem);

        JMenuItem detectItem = new JMenuItem(cfg.imageMagick ? "ImageMagick: installed (recheck)" : "Detect ImageMagick");
        detectItem.setEnabled(null != currentFile);
        detectItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                detectImageMagick();
            }
        });
        menu.add(detectItem);

        JMenuItem pixelMicroscopeItem = new JMenuItem("Pixel Microscope...");
        pixelMicroscopeItem.setEnabled(hasImage);
        pixelMicroscopeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openPixelMicroscope();
            }
        });
        menu.add(pixelMicroscopeItem);

        JMenuItem quadOverlayItem = new JMenuItem("Quad ΔE Overlay...");
        quadOverlayItem.setEnabled(hasImage);
        quadOverlayItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openQuadOverlay();
            }
        });
        menu.add(quadOverlayItem);

        menu.add(rotateMenuItem("Rotate 90°", hasImage, 90));
        menu.add(rotateMenuItem("Rotate 180°", hasImage, 180));
        menu.add(rotateMenuItem("Rotate 270°", hasImage, 270));

        JMenuItem flipHorizontal = new JMenuItem("Flip Horizontal");
        flipHorizontal.setEnabled(hasImage);
        flipHorizontal.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.toggleFlipHorizontal();
            }
        });
        menu.add(flipHorizontal);
        JMenuItem flipVertical = new JMenuItem("Flip Vertical");
        flipVertical.setEnabled(hasImage);
        flipVertical.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.toggleFlipVertical();
            }
        });
        menu.add(flipVertical);

        menu.add(adjustMenuItem("Lighter", hasImage, +BRIGHTNESS_STEP));
        menu.add(adjustMenuItem("Darker", hasImage, -BRIGHTNESS_STEP));
        JMenuItem moreContrast = new JMenuItem("More Contrast");
        moreContrast.setEnabled(hasImage);
        moreContrast.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyAdjustmentAsync(new ContrastStep(), +CONTRAST_STEP);
            }
        });
        menu.add(moreContrast);
        JMenuItem lessContrast = new JMenuItem("Less Contrast");
        lessContrast.setEnabled(hasImage);
        lessContrast.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyAdjustmentAsync(new ContrastStep(), -CONTRAST_STEP);
            }
        });
        menu.add(lessContrast);

        menu.addSeparator();
        JMenu lafMenu = new JMenu("Look & Feel");
        String currentLaf = null == cfg.laf ? defaultLaf() : cfg.laf;
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
     * needed to preview it) and persists it to {@code infimg.json} (MITSA app-data dir) so
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
     * Shows metadata for whatever's currently loaded, in a scrollable
     * dialog. Always starts with {@link #fileInfoHeader} (last
     * modified/pasted, size), blank line, then: for a real file with the
     * {@code imageMagick} config flag set, {@code identify -verbose}'s
     * raw output (full EXIF, ICC profile, histogram — whatever that
     * ImageMagick version reports); otherwise {@link #basicMetadata}
     * (needs no external tool, and is the only option for a clipboard
     * paste — {@code identify} has no file to run against).
     */
    /**
     * Opens the pixel-level microscope (grid view, per-pixel sRGB/YUV/CIELab/
     * HSB, colour-frequency readout) on the current canvas image. Uses the
     * live {@code BufferedImage} directly rather than {@link #currentFile} —
     * a clipboard paste has no backing file, and re-decoding from disk would
     * also risk showing stale pixels if the view has been rotated/flipped/
     * adjusted since load.
     */
    private void openPixelMicroscope() {
        if (null == canvas.source) {
            return;
        }
        String label = null != currentFile ? currentFile.getName() : "(clipboard paste)";
        nl.infcomtec.infimg.pixelmicroscope.PixelMicroscopeFrame microscope
                = new nl.infcomtec.infimg.pixelmicroscope.PixelMicroscopeFrame(new PixelMicroscopeBoundsPersistence());
        try {
            microscope.bigImage.loadImage(canvas.source, label);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to open Pixel Microscope: " + ex,
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        microscope.showFrame();
    }

    /**
     * Opens the CIE76 ΔE quadtree boundary overlay on the current canvas
     * image — a live vector overlay window, never mutating {@code
     * canvas.source}, same "inspect, don't render" spirit as Pixel
     * Microscope. Uses the live {@code BufferedImage} directly for the
     * same reasons {@link #openPixelMicroscope} does.
     */
    private void openQuadOverlay() {
        if (null == canvas.source) {
            return;
        }
        String label = null != currentFile ? currentFile.getName() : "(clipboard paste)";
        nl.infcomtec.infimg.quadmesh.QuadOverlayFrame frame
                = new nl.infcomtec.infimg.quadmesh.QuadOverlayFrame(
                        canvas.source, label, new QuadOverlayBoundsPersistence());
        frame.showFrame();
    }

    /**
     * Backs Quad ΔE Overlay's remembered window bounds with infimg's own
     * {@code infimg.json} (MITSA app-data dir) rather than a second config
     * file — same pattern as {@link PixelMicroscopeBoundsPersistence}.
     */
    private final class QuadOverlayBoundsPersistence
            implements nl.infcomtec.infimg.quadmesh.QuadOverlayFrame.BoundsPersistence {

        @Override
        public Rectangle load() {
            ViewConfig vc = loadConfig().quadOverlay;
            if (null == vc) {
                return null;
            }
            return new Rectangle(vc.x, vc.y, vc.width, vc.height);
        }

        @Override
        public void save(Rectangle bounds) {
            AppConfig cfg = loadConfig();
            ViewConfig vc = new ViewConfig();
            vc.x = bounds.x;
            vc.y = bounds.y;
            vc.width = bounds.width;
            vc.height = bounds.height;
            cfg.quadOverlay = vc;
            writeConfig(cfg);
        }
    }

    /**
     * Backs Pixel Microscope's remembered window bounds with infimg's own
     * {@code infimg.json} (MITSA app-data dir) rather than letting it keep a second config
     * file — one JSON file for the whole app, not one per window.
     */
    private final class PixelMicroscopeBoundsPersistence
            implements nl.infcomtec.infimg.pixelmicroscope.PixelMicroscopeFrame.BoundsPersistence {

        private int lastSideWidth = -1;

        @Override
        public Rectangle load() {
            ViewConfig vc = loadConfig().pixelMicroscope;
            if (null == vc) {
                return null;
            }
            lastSideWidth = loadConfig().pixelMicroscopeSideWidth;
            return new Rectangle(vc.x, vc.y, vc.width, vc.height);
        }

        @Override
        public int loadSideWidth() {
            return lastSideWidth;
        }

        @Override
        public void save(Rectangle bounds, int sideWidth) {
            AppConfig cfg = loadConfig();
            ViewConfig vc = new ViewConfig();
            vc.x = bounds.x;
            vc.y = bounds.y;
            vc.width = bounds.width;
            vc.height = bounds.height;
            cfg.pixelMicroscope = vc;
            cfg.pixelMicroscopeSideWidth = sideWidth;
            writeConfig(cfg);
        }
    }

    private void showMetadata() {
        AppConfig cfg = loadConfig();
        String body = (cfg.imageMagick && null != currentFile) ? runImageMagickIdentify() : basicMetadata();
        if (null == body) {
            return;
        }
        String output = fileInfoHeader() + "\n" + body;
        javax.swing.JTextArea textArea = new javax.swing.JTextArea(output, 40, 100);
        textArea.setEditable(false);
        textArea.setCaretPosition(0);
        String title = null != currentFile ? currentFile.getName() : "(clipboard paste)";
        JOptionPane.showMessageDialog(this, new javax.swing.JScrollPane(textArea),
                "Metadata — " + title, JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * The "usual file information" every OS file browser shows, common to
     * both the ImageMagick and built-in metadata paths: last
     * modified/size for a real file, or their nearest clipboard-paste
     * equivalents — paste time (there's no on-disk modified time) and the
     * in-memory pixel buffer's raw byte size (there's no on-disk size) —
     * when {@link #currentFile} is null.
     */
    private String fileInfoHeader() {
        StringBuilder sb = new StringBuilder();
        if (null != currentFile) {
            sb.append("Last modified: ").append(new java.util.Date(currentFile.lastModified())).append('\n');
            sb.append("Size on disk: ").append(currentFile.length()).append(" bytes\n");
        } else {
            sb.append("Pasted from clipboard: ").append(new java.util.Date(pasteTimeMillis)).append('\n');
            BufferedImage img = canvas.source;
            long pixelBytes = (long) img.getWidth() * img.getHeight() * (img.getColorModel().hasAlpha() ? 4 : 3);
            sb.append("Size in memory: ").append(pixelBytes).append(" bytes (raw pixels, no source file)\n");
        }
        return sb.toString();
    }

    private String runImageMagickIdentify() {
        try {
            Process proc = new ProcessBuilder("identify", "-verbose", currentFile.getAbsolutePath())
                    .redirectErrorStream(true).start();
            String output = new String(proc.getInputStream().readAllBytes());
            proc.waitFor();
            return output;
        } catch (IOException | InterruptedException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Metadata failed", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /**
     * Built-in metadata fallback for when ImageMagick isn't installed (or
     * the current image is a clipboard paste, which {@code identify} can't
     * read anyway): pixel dimensions and color model straight from
     * {@link #canvas}'s already-loaded image, plus — for a real file —
     * its path and the raw EXIF {@code Orientation} tag as read by
     * {@link #readExifOrientation} (the same parse {@link #load} already
     * applies automatically — shown here so the user can see what was
     * detected and corrected for). Deliberately not a general EXIF dump:
     * this codebase parses exactly the one tag it needs, not a whole tag
     * dictionary. The trailing hint differs by why this fallback ran —
     * {@link #showMetadata} only reaches this method when there's no
     * file to hand ImageMagick (a paste) or the {@code imageMagick} flag
     * isn't set, so it's always one or the other, never "ImageMagick's
     * installed but this ran anyway": a paste explains there's simply no
     * file for {@code identify} to read; a real file points at Detect
     * ImageMagick.
     */
    private String basicMetadata() {
        BufferedImage img = canvas.source;
        StringBuilder sb = new StringBuilder();
        if (null != currentFile) {
            sb.append("File: ").append(currentFile.getAbsolutePath()).append('\n');
        }
        sb.append("Pixel dimensions: ").append(img.getWidth()).append('x').append(img.getHeight()).append('\n');
        sb.append("Color model: ").append(img.getColorModel()).append('\n');
        if (null != currentFile) {
            int orientation = readExifOrientation(currentFile);
            sb.append("EXIF Orientation tag: ").append(orientation);
            if (1 != orientation) {
                sb.append(" (auto-corrected to upright on load)");
            }
            sb.append('\n');
        }
        if (null == currentFile) {
            sb.append("\n(Pasted from the clipboard, not a file on disk — ImageMagick's identify has nothing to read here, so this is all the metadata there is.)\n");
        } else {
            sb.append("\n(Install ImageMagick and click Menu -> Detect ImageMagick for full EXIF/ICC/histogram metadata.)\n");
        }
        return sb.toString();
    }

    /**
     * Runs {@code identify -version} to test whether ImageMagick is on
     * PATH, and persists the result to the {@code imageMagick} config flag
     * — the one-click alternative to hand-editing {@code infimg.json} (MITSA app-data dir).
     * Requires a displayed image only so the menu item has something
     * sensible to be enabled/disabled on; the probe itself doesn't touch
     * {@link #currentFile}.
     */
    private void detectImageMagick() {
        boolean found;
        try {
            Process proc = new ProcessBuilder("identify", "-version").redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes();
            found = 0 == proc.waitFor();
        } catch (IOException | InterruptedException ex) {
            found = false;
        }
        AppConfig cfg = loadConfig();
        cfg.imageMagick = found;
        writeConfig(cfg);
        JOptionPane.showMessageDialog(this,
                found ? "ImageMagick found — metadata feature enabled." : "ImageMagick not found on PATH.",
                "Detect ImageMagick", JOptionPane.INFORMATION_MESSAGE);
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
                applyAdjustmentAsync(new BrightnessStep(), deltaL);
            }
        });
        return item;
    }

    /**
     * Builds a single Rotate 90°/180°/270° Menu item — a quick exact
     * square-up alongside the wheel's arbitrary-angle rotate, same
     * {@link ImageCanvas#rotationDeg} the wheel already drives. Each
     * click sets rotation to exactly {@code degrees} (absolute, via
     * {@link ImageCanvas#rotateTo}), not relative to wherever it already
     * was — so Rotate 90° always means "square up at 90°," and clicking
     * it twice in a row stays at 90°, rather than compounding to 180°.
     */
    private JMenuItem rotateMenuItem(String label, boolean enabled, final double degrees) {
        JMenuItem item = new JMenuItem(label);
        item.setEnabled(enabled);
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.rotateTo(degrees);
            }
        });
        return item;
    }

    /** A per-pixel CIELAB adjustment: takes the current L*, a*, b* and a step amount, mutates them in place. */
    private interface LabStep {

        void apply(double[] lab, double step);
    }

    /** Adds {@code step} (an L* offset in points) to L*, clamped 0..100 — the "Lighter"/"Darker" operation. */
    private static final class BrightnessStep implements LabStep {

        @Override
        public void apply(double[] lab, double step) {
            lab[0] = Math.max(0, Math.min(100, lab[0] + step));
        }
    }

    /**
     * Pushes L* through a logistic S-curve centered on middle grey
     * (L*=50), steepened or flattened by {@code step} — the "More/Less
     * Contrast" operation. Unlike a naive RGB contrast stretch, this
     * spreads/compresses perceptual lightness around a fixed pivot rather
     * than raw channel values, so color balance doesn't shift as a side
     * effect of the contrast change.
     */
    private static final class ContrastStep implements LabStep {

        @Override
        public void apply(double[] lab, double step) {
            double normalized = (lab[0] - 50) / 50;
            double k = Math.max(0.05, 1.0 + step);
            double sigmoid = 1.0 / (1.0 + Math.exp(-k * normalized * 3)) * 2 - 1;
            lab[0] = Math.max(0, Math.min(100, 50 + sigmoid * 50));
        }
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
                        markModified();
                    }
                });
            }
        }, "infimg-adjust").start();
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

    /** Cap on {@link AppConfig#recentFiles} — enough to be useful without the dropdown becoming a second file browser. */
    private static final int RECENT_FILES_MAX = 10;

    /** Moves {@code file} to the front of {@link AppConfig#recentFiles} (removing any earlier duplicate) after a successful load, dropping the oldest entry past {@link #RECENT_FILES_MAX}. */
    private void recordRecentFile(File file) {
        AppConfig cfg = loadConfig();
        String path = file.getAbsolutePath();
        cfg.recentFiles.remove(path);
        cfg.recentFiles.add(0, path);
        while (cfg.recentFiles.size() > RECENT_FILES_MAX) {
            cfg.recentFiles.remove(cfg.recentFiles.size() - 1);
        }
        writeConfig(cfg);
    }

    /** Pops up {@link AppConfig#recentFiles} as a menu below {@code invoker}, most-recent first. */
    private void showRecentFilesMenu(java.awt.Component invoker) {
        java.util.List<String> recent = loadConfig().recentFiles;
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        if (recent.isEmpty()) {
            JMenuItem empty = new JMenuItem("(no recent files)");
            empty.setEnabled(false);
            popup.add(empty);
        } else {
            for (final String path : recent) {
                JMenuItem item = new JMenuItem(path);
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        load(new File(path));
                    }
                });
                popup.add(item);
            }
        }
        popup.show(invoker, 0, invoker.getHeight());
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
                cfg.pixelMicroscope = read.pixelMicroscope;
                cfg.pixelMicroscopeSideWidth = read.pixelMicroscopeSideWidth;
                cfg.quadOverlay = read.quadOverlay;
                if (null != read.recentFiles) {
                    cfg.recentFiles = read.recentFiles;
                }
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

    /** Plain POJO mirroring {@code infimg.json} (MITSA app-data dir): the 10 window-position slots plus feature flags for optional external tools. */
    public static final class AppConfig {

        public ViewConfig[] slots = new ViewConfig[10];
        /** Whether ImageMagick's {@code identify} is on PATH — set via Menu → Detect ImageMagick, see MANUAL.md. */
        public boolean imageMagick = false;
        /** One of {@link #LAF_NAMES}; applied at startup before any Swing component is created. Null/unset means {@link #defaultLaf}, not necessarily "System Default" — see there for the actual platform-dependent fallback. */
        public String laf = "System Default";
        /** Last on-screen bounds of the Pixel Microscope window, or null if never opened — shares this file rather than keeping its own, see {@link #openPixelMicroscope}. */
        public ViewConfig pixelMicroscope;
        /** Last width of the Pixel Microscope's neighbourhood+info side column; meaningless while {@link #pixelMicroscope} is null. */
        public int pixelMicroscopeSideWidth;
        /** Last on-screen bounds of the Quad ΔE Overlay window, or null if never opened. */
        public ViewConfig quadOverlay;
        /** Most-recently-loaded files, most recent first, capped at {@link #RECENT_FILES_MAX} — the dropdown next to Load. */
        public java.util.List<String> recentFiles = new java.util.ArrayList<String>();
    }

    /** Plain POJO mirroring one slot of {@code infimg.json} (MITSA app-data dir)'s last on-screen window bounds. */
    public static final class ViewConfig {

        public int x;
        public int y;
        public int width;
        public int height;
    }

    /** Backing file of what's currently loaded, or null (e.g. after {@link #pasteFromClipboard}) — needed by menu features like Metadata that shell out to a file-based external tool. */
    private File currentFile;

    /** Files given on the command line, in argv order — what {@link #loadNext}/{@link #loadPrevious} step through. Empty when infimg was launched with no file, or exactly one. */
    private java.util.List<File> fileList = java.util.Collections.emptyList();

    /** Index into {@link #fileList} of whatever's currently on screen. */
    private int fileIndex;

    /** When {@link #currentFile} is null because the current image came from {@link #pasteFromClipboard}, the epoch-millisecond paste time — Metadata's stand-in for a last-modified timestamp, since a clipboard image has no file. Meaningless (and unread) whenever {@link #currentFile} is non-null. */
    private long pasteTimeMillis;

    /** Title without any "*" (modified) marker — {@link #markModified} appends to this, never to {@link #getTitle}, so it can't accumulate multiple stars. */
    private String baseTitle = "ImageView";

    private void setBaseTitle(String title) {
        baseTitle = title;
        setTitle(baseTitle);
    }

    /**
     * Marks the title with a trailing "*" — purely informative, not
     * enforced (no unsaved-changes prompt on Exit, see the toolbar's Exit
     * tooltip) — once the on-screen pixels differ from {@link #currentFile}
     * (or the clipboard grab {@link #pasteFromClipboard} loaded) and Save
     * would therefore write something new. Deliberately triggered only by
     * pixel-level edits (Menu → Lighter/Darker/More Contrast/Less
     * Contrast) — zoom/rotate/pan aren't "modifications" to the image
     * itself, just how it's currently being looked at; {@link #save}
     * bakes in whichever view is current either way.
     */
    private void markModified() {
        if (!baseTitle.endsWith("*")) {
            setTitle(baseTitle + "*");
        }
    }

    /**
     * Reads {@code file} and displays it. The read itself runs on a
     * background thread via {@link SwingWorker} — not the EDT — so a slow
     * source (a NAS share over CIFS has been the noticeable case) blocks
     * only that worker thread, leaving the EDT free to actually paint the
     * "Loading" placeholder ({@link ImageCanvas#loading}, set before the
     * worker starts) instead of freezing the whole window for however
     * long the read takes. {@link SwingWorker#done()} always runs back on
     * the EDT, which is where the loaded image (or the failure dialog)
     * gets applied.
     */
    private void load(final File file) {
        load(file, java.util.Collections.<CliOp>emptyList());
    }

    /** Same as {@link #load(File)}, but runs {@code ops} once, in order, right after the image is on screen — used only by {@link #main} for the initial file's {@code --rotate}/{@code --flip-hor}/etc. flags. */
    private void load(final File file, final java.util.List<CliOp> ops) {
        canvas.loading = true;
        canvas.repaint();
        new SwingWorker<BufferedImage, Void>() {
            private int orientation;
            private IOException failure;

            @Override
            protected BufferedImage doInBackground() {
                try {
                    BufferedImage img = ImageIO.read(file);
                    orientation = readExifOrientation(file);
                    return img;
                } catch (IOException ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                canvas.loading = false;
                try {
                    if (null != failure) {
                        JOptionPane.showMessageDialog(ImageView.this, failure.getMessage(), "Load failed", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    BufferedImage img = get();
                    if (null == img) {
                        JOptionPane.showMessageDialog(ImageView.this, "Not a readable image: " + file, "Load failed", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    canvas.setImage(applyExifOrientation(img, orientation), exifRotationDegrees(orientation));
                    currentFile = file;
                    setBaseTitle(file.getName());
                    recordRecentFile(file);
                    for (CliOp op : ops) {
                        op.apply(ImageView.this);
                    }
                } catch (java.util.concurrent.ExecutionException | InterruptedException ex) {
                    JOptionPane.showMessageDialog(ImageView.this, ex.getMessage(), "Load failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    canvas.repaint();
                }
            }
        }.execute();
    }

    /** Loads {@link #fileList}{@code [}{@link #fileIndex}{@code + 1]} — the file after whatever's on screen, in the order given on the command line. No-op (button stays disabled) at the last file or when infimg wasn't launched with a file list. */
    private void loadNext() {
        if (fileIndex + 1 < fileList.size()) {
            fileIndex++;
            load(fileList.get(fileIndex));
        }
    }

    /** Loads {@link #fileList}{@code [}{@link #fileIndex}{@code - 1]} — the file before whatever's on screen. No-op at the first file or when infimg wasn't launched with a file list. */
    private void loadPrevious() {
        if (fileIndex > 0) {
            fileIndex--;
            load(fileList.get(fileIndex));
        }
    }

    /**
     * Rotates/flips {@code img} so it displays upright per {@code
     * orientation} (an EXIF {@code Orientation} tag value, 1–8,
     * TIFF/EXIF spec, as returned by {@link #readExifOrientation}) — the
     * same correction every other viewer (Nemo, Photos, browsers)
     * applies but that {@link ImageIO#read} does not; it hands back raw
     * sensor pixels, ignoring the tag entirely. Returns {@code img}
     * unchanged for the trivial value 1 (already upright).
     */
    private BufferedImage applyExifOrientation(BufferedImage img, int orientation) {
        switch (orientation) {
            case 2:
            case 5:
                return flip(img, true, false);
            case 4:
            case 7:
                return flip(img, false, true);
            default:
                return img;
        }
    }

    /**
     * The clockwise rotation {@code orientation} calls for, in degrees —
     * fed straight into {@link ImageCanvas#rotationDeg} by {@link #load}
     * so EXIF correction lives in the same single rotation state as the
     * mouse wheel and Menu → Rotate, instead of being partly baked into
     * pixels: one source of truth for "how rotated is this," always
     * accurate however it got there.
     */
    private double exifRotationDegrees(int orientation) {
        switch (orientation) {
            case 5:
            case 6:
                return 90;
            case 3:
                return 180;
            case 7:
            case 8:
                return 270;
            default:
                return 0;
        }
    }

    /** Rotates {@code img} clockwise by {@code degrees} (must be 90, 180, or 270), swapping width/height for 90/270. */
    private BufferedImage rotate(BufferedImage img, int degrees) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean swap = 90 == degrees || 270 == degrees;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, img.getType());
        Graphics2D g = out.createGraphics();
        AffineTransform at = new AffineTransform();
        if (90 == degrees) {
            at.translate(h, 0);
        } else if (180 == degrees) {
            at.translate(w, h);
        } else if (270 == degrees) {
            at.translate(0, w);
        }
        at.rotate(Math.toRadians(degrees));
        g.drawImage(img, at, null);
        g.dispose();
        return out;
    }

    /** Flips {@code img} horizontally and/or vertically, same dimensions in, same dimensions out. */
    private BufferedImage flip(BufferedImage img, boolean horizontal, boolean vertical) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, img.getType());
        Graphics2D g = out.createGraphics();
        AffineTransform at = new AffineTransform();
        at.translate(horizontal ? w : 0, vertical ? h : 0);
        at.scale(horizontal ? -1 : 1, vertical ? -1 : 1);
        g.drawImage(img, at, null);
        g.dispose();
        return out;
    }

    /** The one {@code IIOMetadata} tree format {@link #findExifSegment} knows how to walk — JPEG's APP1-as-opaque-bytes shape. Other formats (PNG, GIF, BMP...) either carry no EXIF or expose it in a completely different tree shape this code doesn't parse. */
    private static final String JPEG_METADATA_FORMAT = "javax_imageio_jpeg_image_1.0";

    /**
     * Reads the EXIF {@code Orientation} tag (IFD0, tag {@code 0x0112})
     * straight out of the JPEG's APP1 segment. {@code ImageIO}'s
     * {@value #JPEG_METADATA_FORMAT} metadata tree exposes that whole
     * segment as one opaque {@code unknown} node holding raw bytes
     * ({@code "Exif\0\0"} + a TIFF header + an IFD) rather than parsed
     * fields, so this walks the IFD by hand. Returns 1 (upright, i.e. a
     * no-op) if the file has no metadata, isn't a JPEG, or the tag isn't
     * present — the same as if orientation were explicitly 1. Only
     * checks for/asks for {@value #JPEG_METADATA_FORMAT} specifically:
     * asking any {@link IIOMetadata} for a tree format it doesn't
     * support throws {@link IllegalArgumentException} rather than
     * returning null, which a same-shaped bug once let escape all the
     * way up to failing a perfectly good PNG's Load with a stack-trace
     * dialog (2026-08-13) — every exit here is a plain "no orientation
     * info," never a thrown exception a caller has to know to catch.
     */
    private int readExifOrientation(File file) {
        try (javax.imageio.stream.ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            if (null == iis) {
                return 1;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return 1;
            }
            javax.imageio.ImageReader reader = readers.next();
            reader.setInput(iis);
            javax.imageio.metadata.IIOMetadata meta = reader.getImageMetadata(0);
            reader.dispose();
            if (null == meta || !supportsFormat(meta, JPEG_METADATA_FORMAT)) {
                return 1;
            }
            byte[] exif = findExifSegment(meta.getAsTree(JPEG_METADATA_FORMAT));
            return null == exif ? 1 : parseOrientation(exif);
        } catch (IOException | IllegalArgumentException ex) {
            return 1;
        }
    }

    private boolean supportsFormat(javax.imageio.metadata.IIOMetadata meta, String formatName) {
        for (String name : meta.getMetadataFormatNames()) {
            if (formatName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Depth-first search of the JPEG metadata tree for the raw byte[] of the APP1 segment starting with {@code "Exif\0\0"}. */
    private byte[] findExifSegment(org.w3c.dom.Node node) {
        if (node instanceof javax.imageio.metadata.IIOMetadataNode) {
            Object userObject = ((javax.imageio.metadata.IIOMetadataNode) node).getUserObject();
            if (userObject instanceof byte[]) {
                byte[] bytes = (byte[]) userObject;
                if (bytes.length > 6 && 'E' == bytes[0] && 'x' == bytes[1] && 'i' == bytes[2] && 'f' == bytes[3]) {
                    return bytes;
                }
            }
        }
        org.w3c.dom.Node child = node.getFirstChild();
        while (null != child) {
            byte[] found = findExifSegment(child);
            if (null != found) {
                return found;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Parses the TIFF IFD0 in {@code exif} (starting after the 6-byte
     * {@code "Exif\0\0"} prefix) for tag {@code 0x0112} (Orientation) and
     * returns its short value, or 1 if not found/malformed.
     */
    private int parseOrientation(byte[] exif) {
        int tiffStart = 6;
        if (exif.length < tiffStart + 8) {
            return 1;
        }
        boolean bigEndian = 'M' == exif[tiffStart];
        int ifdOffset = readInt32(exif, tiffStart + 4, bigEndian);
        int ifdStart = tiffStart + ifdOffset;
        if (ifdStart + 2 > exif.length) {
            return 1;
        }
        int entryCount = readInt16(exif, ifdStart, bigEndian);
        for (int i = 0; i < entryCount; i++) {
            int entryOffset = ifdStart + 2 + i * 12;
            if (entryOffset + 12 > exif.length) {
                break;
            }
            int tag = readInt16(exif, entryOffset, bigEndian);
            if (0x0112 == tag) {
                return readInt16(exif, entryOffset + 8, bigEndian);
            }
        }
        return 1;
    }

    private int readInt16(byte[] b, int off, boolean bigEndian) {
        int b0 = b[off] & 0xFF;
        int b1 = b[off + 1] & 0xFF;
        return bigEndian ? (b0 << 8) | b1 : (b1 << 8) | b0;
    }

    private int readInt32(byte[] b, int off, boolean bigEndian) {
        int b0 = b[off] & 0xFF;
        int b1 = b[off + 1] & 0xFF;
        int b2 = b[off + 2] & 0xFF;
        int b3 = b[off + 3] & 0xFF;
        return bigEndian ? (b0 << 24) | (b1 << 16) | (b2 << 8) | b3 : (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
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
            pasteTimeMillis = System.currentTimeMillis();
            setBaseTitle(String.format("(clip) %tH:%<tM:%<tS.%<tL", pasteTimeMillis));
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
    /** Callback for {@link ImageCanvas} to report a rotation change to whatever's displaying it (the toolbar's degree label). */
    private interface RotationListener {

        void rotationChanged(double degrees);
    }

    private static final class ImageCanvas extends JComponent {

        private BufferedImage source;
        private double zoom = 1.0;
        private double rotationDeg = 0.0;
        /** Mirrored left-right in view space, applied to the already-rotated image (see {@link #paintComponent}) — a flip is "mirror what's on screen," not a pre-rotation pixel operation, so it must compose after rotate, not before. */
        private boolean flipH = false;
        /** Mirrored top-bottom in view space — see {@link #flipH}. */
        private boolean flipV = false;
        private double panX = 0.0;
        private double panY = 0.0;
        private boolean rotateOnWheel = false;
        private int dragLastX;
        private int dragLastY;
        /** Notified with the current {@link #rotationDeg} whenever it changes, so the toolbar's degree label can stay in sync without polling. */
        private RotationListener rotationListener;
        /** Set by {@link ImageView#load} while the (blocking, on-EDT) file read is in flight, so {@link #paintComponent} can show a "Loading" placeholder — the only feedback available on a slow read (e.g. a NAS share) since the read itself isn't backgrounded. */
        private boolean loading = false;

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
                        fireRotationChanged();
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
            setImage(img, 0.0);
        }

        /**
         * Same as {@link #setImage(BufferedImage)}, but starts at
         * {@code initialDeg} instead of 0 — for {@link ImageView#load},
         * which passes the file's EXIF-implied rotation here so it's the
         * same single rotation state the wheel and Menu → Rotate use,
         * rather than baked into pixels separately from it.
         */
        void setImage(BufferedImage img, double initialDeg) {
            source = img;
            rotationDeg = initialDeg;
            flipH = false;
            flipV = false;
            fireRotationChanged();
            fitToWindow();
        }

        /** Sets rotation to exactly {@code degrees} (not relative to whatever it was) and repaints — used by Menu → Rotate 90/180/270, so each is an absolute square-up, not a nudge that compounds with wherever the wheel left things. */
        void rotateTo(double degrees) {
            rotationDeg = degrees;
            fireRotationChanged();
            repaint();
        }

        /** Toggles left-right mirroring and repaints — used by Menu → Flip Horizontal. */
        void toggleFlipHorizontal() {
            flipH = !flipH;
            repaint();
        }

        /** Toggles top-bottom mirroring and repaints — used by Menu → Flip Vertical. */
        void toggleFlipVertical() {
            flipV = !flipV;
            repaint();
        }

        private void fireRotationChanged() {
            if (null != rotationListener) {
                rotationListener.rotationChanged(rotationDeg);
            }
        }

        /** Swaps {@link #source} in place — unlike {@link #setImage}, keeps zoom/rotation/pan untouched, for pixel-adjustment previews (e.g. brightness) that shouldn't reset the user's current view. */
        void replaceImageKeepingView(BufferedImage img) {
            source = img;
            repaint();
        }

        /**
         * Rescales (up or down) and re-centers {@link #source} to exactly
         * fill the panel's current size at whatever angle {@link
         * #rotationDeg} currently is — the same math {@link #setImage}
         * applies on load, exposed separately for the toolbar's "Fit"
         * button, for after the user has manually resized the window,
         * wandered off with zoom/pan, or rotated (wheel or Menu) since
         * the last fit. "Show the entire image" at an arbitrary angle
         * means fitting the image's rotated axis-aligned bounding box —
         * width {@code |w·cos θ| + |h·sin θ|}, height {@code |w·sin θ| +
         * |h·cos θ|} for source dimensions {@code w × h} — into the
         * window, not the raw unrotated {@code w × h} footprint; at a
         * 45° angle that bounding box is bigger than the source in both
         * dimensions, so expect Fit to zoom out further than at 0°/90°,
         * by design — the whole image genuinely takes up more on-screen
         * room once its corners swing wide.
         */
        void fitToWindow() {
            panX = 0.0;
            panY = 0.0;
            if (null == source) {
                return;
            }
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            double rad = Math.toRadians(rotationDeg);
            double cos = Math.abs(Math.cos(rad));
            double sin = Math.abs(Math.sin(rad));
            double boundsW = source.getWidth() * cos + source.getHeight() * sin;
            double boundsH = source.getWidth() * sin + source.getHeight() * cos;
            // Round the fitted zoom down to the pixel below (Math.ceil on the
            // bounding box, not Math.floor on the ratio, so it stays exact
            // for the un-rotated 0°/180° case): floating-point error in
            // sin/cos can otherwise land zoom a hair high, scaling the image
            // one pixel larger than the window on one axis — clipped, not
            // "fit". Erring slightly smaller always leaves a fit-and-clean
            // margin instead, never a clipped edge.
            zoom = Math.min(w / Math.ceil(boundsW), h / Math.ceil(boundsH));
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (loading) {
                paintLoading(g);
                return;
            }
            if (null == source) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            AffineTransform at = new AffineTransform();
            at.translate(getWidth() / 2.0, getHeight() / 2.0);
            // Flip must be written BEFORE rotate here to end up applied
            // AFTER it: AffineTransform composes calls in reverse of
            // writing order (each subsequent call is applied earlier to
            // the source point — see the class doc above). Flip needs to
            // mirror the already-rotated image — the EXIF-corrected
            // orchid photo is rotated 90° first, then flipped, matching
            // what the user sees on screen when they ask for a flip, not
            // the raw file's original unrotated orientation — so writing
            // it here, ahead of rotate(), is what actually places it
            // after rotate() in the real per-pixel order.
            at.scale(flipH ? -1 : 1, flipV ? -1 : 1);
            at.rotate(Math.toRadians(rotationDeg));
            at.scale(zoom, zoom);
            at.translate(-source.getWidth() / 2.0 + panX, -source.getHeight() / 2.0 + panY);
            g2.drawImage(source, at, null);
        }

        /** Draws "Loading" centered in a big font over the (cleared) canvas — the only progress feedback available for a slow, on-EDT file read (e.g. a NAS share over CIFS), since that read isn't backgrounded. */
        private void paintLoading(java.awt.Graphics g) {
            String text = "Loading…";
            g.setColor(Color.LIGHT_GRAY);
            g.setFont(getFont().deriveFont(java.awt.Font.BOLD, 48f));
            java.awt.FontMetrics fm = g.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(text, x, y);
        }
    }
}
