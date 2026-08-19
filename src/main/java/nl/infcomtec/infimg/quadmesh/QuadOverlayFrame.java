package nl.infcomtec.infimg.quadmesh;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

/**
 * Shows the current image with CIE76 ΔE quadtree boundaries drawn as a
 * live vector overlay — never baked into the pixels, redrawn on every
 * paint, same "inspect, don't render" spirit as Pixel Microscope. The
 * quadtree itself is recomputed only when the image or the ΔE preset
 * changes, not on every repaint.
 */
public final class QuadOverlayFrame extends JFrame {

    /**
     * Named presets for the common case — see infimg's feature-scope rule
     * (CLAUDE.md), same reasoning as the brightness/contrast
     * slider-to-buttons rewrite. A "Custom..." menu item (see
     * {@link #buildMenuBar}) is the escape hatch for anyone who wants a
     * specific ΔE value instead of these three.
     */
    public enum Preset {
        COARSE(40.0), FINE(25.0), FINEST(10.0);

        public final double deltaE;

        Preset(double deltaE) {
            this.deltaE = deltaE;
        }
    }

    /** Backs this window's remembered bounds with infimg's own config file rather than a second one. */
    public interface BoundsPersistence {

        Rectangle load();

        void save(Rectangle bounds);
    }

    private final BufferedImage source;
    private final OverlayPanel panel;
    private final BoundsPersistence persistence;
    private double deltaE = Preset.FINE.deltaE;

    public QuadOverlayFrame(BufferedImage source, String label, BoundsPersistence persistence) {
        super("Quad ΔE Overlay — " + label);
        this.source = source;
        this.persistence = persistence;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        panel = new OverlayPanel();
        add(panel, BorderLayout.CENTER);
        setJMenuBar(buildMenuBar());

        Rectangle bounds = persistence.load();
        if (null == bounds) {
            Dimension screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds().getSize();
            int w = Math.min(1000, screen.width - 100);
            int h = Math.min(800, screen.height - 100);
            setBounds(100, 100, w, h);
        } else {
            setBounds(bounds);
        }
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                persistence.save(getBounds());
            }

            @Override
            public void componentResized(ComponentEvent e) {
                persistence.save(getBounds());
            }
        });
    }

    public void showFrame() {
        setVisible(true);
        panel.recompute();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu presetMenu = new JMenu("ΔE Detail");
        ButtonGroup group = new ButtonGroup();
        for (final Preset p : Preset.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(presetLabel(p), p.deltaE == deltaE);
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    deltaE = p.deltaE;
                    panel.recompute();
                }
            });
            group.add(item);
            presetMenu.add(item);
        }
        presetMenu.addSeparator();
        JMenuItem customItem = new JMenuItem("Custom...");
        customItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                promptCustomDeltaE();
            }
        });
        presetMenu.add(customItem);
        bar.add(presetMenu);

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyToClipboard();
            }
        });
        bar.add(copyItem);

        JMenuItem saveItem = new JMenuItem("Save...");
        saveItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
            }
        });
        bar.add(saveItem);

        return bar;
    }

    /**
     * Renders exactly what {@link OverlayPanel} currently shows (scaled
     * image + quad boundaries), same content both {@link #copyToClipboard}
     * and {@link #save} use. {@code TYPE_INT_RGB}, not {@code _ARGB} — same
     * Linux/X11 clipboard PNG round-trip bug {@code
     * ImageView.renderCurrentView} works around.
     */
    private BufferedImage renderView() {
        BufferedImage out = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        panel.paint(g);
        g.dispose();
        return out;
    }

    private void copyToClipboard() {
        final BufferedImage view = renderView();
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

    private void save() {
        JFileChooser chooser = new JFileChooser();
        if (JFileChooser.APPROVE_OPTION != chooser.showSaveDialog(this)) {
            return;
        }
        File file = chooser.getSelectedFile();
        String name = file.getName();
        String format = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "png";
        try {
            ImageIO.write(renderView(), format, file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String presetLabel(Preset p) {
        String name;
        switch (p) {
            case COARSE:
                name = "Coarse";
                break;
            case FINEST:
                name = "Finest";
                break;
            default:
                name = "Fine";
                break;
        }
        return name + " (ΔE " + (int) p.deltaE + ")";
    }

    /** Escape hatch for a specific ΔE value — see {@link Preset}'s doc. */
    private void promptCustomDeltaE() {
        String input = JOptionPane.showInputDialog(this,
                "CIE76 ΔE limit (smaller = more detail):", deltaE);
        if (null == input) {
            return;
        }
        try {
            double value = Double.parseDouble(input.trim());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            deltaE = value;
            panel.recompute();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a positive number.", "Invalid ΔE", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Draws the source image scaled to fit, with the current Quad's leaf
     * rectangles stroked on top in image space via the same scale
     * transform — vector overlay, not pixels burned into a copy.
     */
    private final class OverlayPanel extends JPanel {

        private Quad quad;

        void recompute() {
            BufferedImageSource src = new BufferedImageSource(source);
            quad = new Quad(src, new DeltaECrit(deltaE));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (null == source) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale = Math.min(
                        (double) getWidth() / source.getWidth(),
                        (double) getHeight() / source.getHeight());
                int drawW = (int) (source.getWidth() * scale);
                int drawH = (int) (source.getHeight() * scale);
                g2.drawImage(source, 0, 0, drawW, drawH, null);
                if (null != quad) {
                    g2.scale(scale, scale);
                    g2.setColor(Color.BLACK);
                    quad.traverse(new QuadProcessor() {
                        @Override
                        public boolean process(Quad q) {
                            g2.draw(q.rect);
                            return true;
                        }
                    });
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
