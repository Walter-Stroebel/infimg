/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.infimg.pixelmicroscope;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import nl.infcomtec.jacksonwrap.AwtColor;

/**
 * Most common Swing component properties, mostly meant to be serialized using
 * Jackson. Null therefore means default or none. Common sense applies.
 */
public class SwingProps {

    /**
     * Foreground or text color. @see AwtColor. Alpha will be true, do not omit.
     */
    public AwtColor fore;
    /**
     * Background color.Alpha will be true, do not omit.
     */
    public AwtColor back;
    /**
     * If present will create a black titled line border. Defaults to a blank
     * string if only bColor is specified;
     */
    public String bTitle;
    /**
     * If present will create a colored titled line border.
     */
    public AwtColor bColor;
    /**
     * Mind that L&amp;F in use might not support these very well.
     */
    public String toolTip;
    /**
     * This must be a Base64 encoded byte array for ImageIO.
     */
    public String icon;
    /**
     * If not present or font is not installed will derive fromColor the default
 font.
     */
    public String fontName;
    /**
     * The usual.
     */
    public Integer fontStyle;
    /**
     * In points. Note that AWT font is integer, not float.
     */
    public Integer fontSize;

    public void apply(Component comp) {
        apply(comp, this);
    }

    public static void apply(Component comp, SwingProps props) {
        if (props == null || comp == null) {
            return;
        }

        // Colors (ARGB)
        if (props.fore != null) {
            comp.setForeground(props.fore.awt());
        }
        if (props.back != null) {
            comp.setBackground(props.back.awt());
        }

        // Borders
        if (props.bTitle != null || props.bColor != null) {
            Color borderCol = (props.bColor != null) ? props.bColor.awt() : Color.BLACK;
            String title = (props.bTitle != null) ? props.bTitle : "";

            if (comp instanceof JComponent) {
                ((JComponent) comp).setBorder(BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(borderCol), title));
            }
        }

        // Tooltips
        if (props.toolTip != null && comp instanceof JComponent) {
            ((JComponent) comp).setToolTipText(props.toolTip);
        }

        // Fonts
        if (props.fontName != null || props.fontStyle != null || props.fontSize != null) {
            Font current = comp.getFont();
            String name = (props.fontName != null) ? props.fontName : current.getName();
            int style = current.getStyle();
            if (props.fontStyle != null) {
                style = props.fontStyle;
            }
            int size = current.getSize();
            if (props.fontSize != null) {
                size = props.fontSize;
            }
            comp.setFont(new Font(name, style, size));
        }

        // Icon (Base64)
        if (props.icon != null) {
            try {
                byte[] bt = Base64.getDecoder().decode(props.icon);
                Image img = ImageIO.read(new ByteArrayInputStream(bt));
                if (img != null) {
                    ImageIcon icon = new ImageIcon(img);
                    if (comp instanceof JLabel) {
                        ((JLabel) comp).setIcon(icon);
                    } else if (comp instanceof AbstractButton) {
                        ((AbstractButton) comp).setIcon(icon);
                    }
                }
            } catch (Exception e) {
                // Silently fail or log as per "Common sense applies"
            }
        }
    }

}
