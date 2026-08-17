package nl.infcomtec.infimg.pixelmicroscope;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JPanel;
import nl.infcomtec.infimg.ColorBase;
import nl.infcomtec.infimg.ColorImage;
import nl.infcomtec.infimg.EnhancedColor;
import nl.infcomtec.infimg.TriElm;
import nl.infcomtec.infimg.YUV;

/**
 * Plain label grid (deliberately not a JTable) showing every representation
 * of the pixel last reported by {@link BigImagePanel}: position (absolute
 * and as a percentage of image size), hex/RGB, YUV, CIELab, HSB (EnhancedColor
 * has no true HSL), and how common this exact colour is across the image
 * (free from {@link ColorImage}'s already-built {@link ColorBase} inventory
 * — no extra scan needed).
 */
public class PixelInfoPanel extends JPanel {

    private final JLabel posValue = new JLabel("-");
    private final JLabel pctValue = new JLabel("-");
    private final JLabel hexValue = new JLabel("-");
    private final JLabel rgbValue = new JLabel("-");
    private final JLabel yuvValue = new JLabel("-");
    private final JLabel labValue = new JLabel("-");
    private final JLabel hsbValue = new JLabel("-");
    private final JLabel freqValue = new JLabel("-");

    public PixelInfoPanel() {
        setLayout(new GridBagLayout());
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        posValue.setFont(mono);
        pctValue.setFont(mono);
        hexValue.setFont(mono);
        rgbValue.setFont(mono);
        yuvValue.setFont(mono);
        labValue.setFont(mono);
        hsbValue.setFont(mono);
        freqValue.setFont(mono);

        addRow(0, "Position", posValue);
        addRow(1, "Position %", pctValue);
        addRow(2, "Hex", hexValue);
        addRow(3, "sRGB", rgbValue);
        addRow(4, "YUV", yuvValue);
        addRow(5, "CIELab", labValue);
        addRow(6, "HSB", hsbValue);
        addRow(7, "Frequency", freqValue);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = 8;
        filler.weighty = 1.0;
        add(new JLabel(), filler);
    }

    private void addRow(int row, String labelText, JLabel valueLabel) {
        GridBagConstraints labelC = new GridBagConstraints();
        labelC.gridx = 0;
        labelC.gridy = row;
        labelC.anchor = GridBagConstraints.WEST;
        labelC.insets = new Insets(2, 6, 2, 10);
        JLabel label = new JLabel(labelText + ":");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        add(label, labelC);

        GridBagConstraints valueC = new GridBagConstraints();
        valueC.gridx = 1;
        valueC.gridy = row;
        valueC.anchor = GridBagConstraints.WEST;
        valueC.weightx = 1.0;
        valueC.fill = GridBagConstraints.HORIZONTAL;
        valueC.insets = new Insets(2, 0, 2, 6);
        add(valueLabel, valueC);
    }

    public void setPixel(ColorImage image, int px, int py) {
        int rgb = image.pixels[py * image.w + px];
        Color c = new Color(rgb);
        posValue.setText(String.format("x=%d, y=%d", px, py));
        pctValue.setText(String.format("x=%5.1f%%  y=%5.1f%%",
                100.0 * px / image.w, 100.0 * py / image.h));
        hexValue.setText(String.format("#%06X", rgb & 0xFFFFFF));
        rgbValue.setText(String.format("R=%3d  G=%3d  B=%3d", c.getRed(), c.getGreen(), c.getBlue()));

        YUV yuv = new YUV(c);
        yuvValue.setText(String.format("Y=%.2f  U=%.2f  V=%.2f", yuv.Y, yuv.U, yuv.V));

        EnhancedColor ec = new EnhancedColor(rgb);
        double[] lab = ec.getCIELAB();
        labValue.setText(String.format("L=%.2f  a=%.2f  b=%.2f", lab[0], lab[1], lab[2]));

        float[] hsb = ec.getHSB();
        hsbValue.setText(String.format("H=%.3f  S=%.3f  B=%.3f", hsb[0], hsb[1], hsb[2]));

        TriElm key = new TriElm();
        key.v0 = (short) c.getRed();
        key.v1 = (short) c.getGreen();
        key.v2 = (short) c.getBlue();
        ColorBase.TriLabColor entry = image.cb.cache.get(key);
        long count = (null == entry) ? 0 : entry.count;
        double pct = 100.0 * count / image.pixels.length;
        freqValue.setText(String.format("%,d px  (%.4f%% of image)", count, pct));
    }
}
