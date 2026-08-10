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
import javax.swing.JOptionPane;
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
 */
public final class ImageView extends JFrame {

    private static final File CONFIG_FILE = new File(System.getProperty("user.home"), ".infimg.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ImageCanvas canvas = new ImageCanvas();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ImageView view = new ImageView();
                view.setVisible(true);
                if (args.length > 0) {
                    view.load(new File(args[0]));
                }
            }
        });
    }

    public ImageView() {
        super("ImageView");
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

        ViewConfig cfg = loadConfig();
        if (null != cfg && cfg.width > 0 && cfg.height > 0) {
            setBounds(cfg.x, cfg.y, cfg.width, cfg.height);
        } else {
            Dimension screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds().getSize();
            setSize(Math.min(1200, screen.width), Math.min(900, screen.height));
            setLocationRelativeTo(null);
        }
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                saveConfig();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                saveConfig();
            }
        });
    }

    private void saveConfig() {
        Rectangle b = getBounds();
        ViewConfig cfg = new ViewConfig();
        cfg.x = b.x;
        cfg.y = b.y;
        cfg.width = b.width;
        cfg.height = b.height;
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE, cfg);
        } catch (IOException ex) {
            Logger.getLogger(ImageView.class.getName()).log(Level.WARNING, "Could not save " + CONFIG_FILE, ex);
        }
    }

    private static ViewConfig loadConfig() {
        if (!CONFIG_FILE.isFile()) {
            return null;
        }
        try {
            return MAPPER.readValue(CONFIG_FILE, ViewConfig.class);
        } catch (IOException ex) {
            Logger.getLogger(ImageView.class.getName()).log(Level.WARNING, "Could not read " + CONFIG_FILE, ex);
            return null;
        }
    }

    /** Plain POJO mirroring {@code ~/.infimg.json}'s last on-screen window bounds. */
    public static final class ViewConfig {

        public int x;
        public int y;
        public int width;
        public int height;
    }

    private void load(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (null == img) {
                JOptionPane.showMessageDialog(this, "Not a readable image: " + file, "Load failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            canvas.setImage(img);
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
        BufferedImage out = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        canvas.paint(g);
        g.dispose();
        try {
            ImageIO.write(out, format, file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Draws {@link #source} under a single affine transform combining zoom,
     * rotation, and pan. Point order (last-applied-first, per
     * {@link AffineTransform}'s concatenation semantics): the image is
     * first centered on the origin, then scaled, then rotated, then
     * translated to its on-screen position (panel center plus drag pan) —
     * the standard scale/rotate/translate composition.
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
                    panX += e.getX() - dragLastX;
                    panY += e.getY() - dragLastY;
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
            at.translate(getWidth() / 2.0 + panX, getHeight() / 2.0 + panY);
            at.rotate(Math.toRadians(rotationDeg));
            at.scale(zoom, zoom);
            at.translate(-source.getWidth() / 2.0, -source.getHeight() / 2.0);
            g2.drawImage(source, at, null);
        }
    }
}
