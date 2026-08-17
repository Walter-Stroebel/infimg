/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.jacksonwrap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.awt.Color;

/**
 * AwtColor provides a bridge between Jackson-serialized strings and
 * java.awt.Color.
 * <p>
 * This class supports:
 * <ul>
 * <li>Standard AWT Color names (e.g., "RED", "BLUE").</li>
 * <li>Hexadecimal strings in #RRGGBB format.</li>
 * <li>Hexadecimal strings with alpha in #AARRGGBB format.</li>
 * </ul>
 * Failures will not return null but ORANGE as a visible signal of SN:AFU.
 */
public class AwtColor {

    public static final AwtColor WHITE = new AwtColor(Color.WHITE, "WHITE");
    public static final AwtColor LIGHT_GRAY = new AwtColor(Color.LIGHT_GRAY, "LIGHT_GRAY");
    public static final AwtColor GRAY = new AwtColor(Color.GRAY, "GRAY");
    public static final AwtColor DARK_GRAY = new AwtColor(Color.DARK_GRAY, "DARK_GRAY");
    public static final AwtColor BLACK = new AwtColor(Color.BLACK, "BLACK");
    public static final AwtColor RED = new AwtColor(Color.RED, "RED");
    public static final AwtColor PINK = new AwtColor(Color.PINK, "PINK");
    public static final AwtColor ORANGE = new AwtColor(Color.ORANGE, "ORANGE");
    public static final AwtColor YELLOW = new AwtColor(Color.YELLOW, "YELLOW");
    public static final AwtColor GREEN = new AwtColor(Color.GREEN, "GREEN");
    public static final AwtColor MAGENTA = new AwtColor(Color.MAGENTA, "MAGENTA");
    public static final AwtColor CYAN = new AwtColor(Color.CYAN, "CYAN");
    public static final AwtColor BLUE = new AwtColor(Color.BLUE, "BLUE");

    private final Color color;
    private final String representation;

    private AwtColor(Color color, String representation) {
        this.color = color;
        this.representation = representation;
    }

    public Color awt() {
        return color;
    }

    @JsonValue
    public String json() {
        return representation;
    }

    @JsonCreator
    public static AwtColor from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ORANGE;
        }

        String input = value.trim();
        String lookupName = input.startsWith("#") ? input.substring(1) : input;

        AwtColor constant = getConstant(lookupName.toUpperCase());
        if (constant != null) {
            return constant;
        }

        if (input.startsWith("#")) {
            try {
                return parseHex(input);
            } catch (Exception e) {
                System.err.format("AwtColor: Failed to parse hex '%s'\n", input);
            }
        }

        return ORANGE;
    }

    private static AwtColor parseHex(String hex) {
        String raw = hex.substring(1);
        if (raw.length() == 8) {
            int bits = (int) Long.parseLong(raw, 16);
            return new AwtColor(new Color(bits, true), hex.toUpperCase());
        } else if (raw.length() == 6) {
            return new AwtColor(Color.decode(hex), hex.toUpperCase());
        }
        throw new IllegalArgumentException("Hex length must be 6 or 8 digits.");
    }

    private static AwtColor getConstant(String name) {
        switch (name) {
            case "WHITE":
                return WHITE;
            case "LIGHT_GRAY":
                return LIGHT_GRAY;
            case "GRAY":
                return GRAY;
            case "DARK_GRAY":
                return DARK_GRAY;
            case "BLACK":
                return BLACK;
            case "RED":
                return RED;
            case "PINK":
                return PINK;
            case "YELLOW":
                return YELLOW;
            case "GREEN":
                return GREEN;
            case "MAGENTA":
                return MAGENTA;
            case "CYAN":
                return CYAN;
            case "BLUE":
                return BLUE;
            default:
                return ORANGE;
        }
    }

    /**
     * Uses a constant color if identical, fall back to hex if not.
     *
     * @param color The user wants, can have Alpha.
     * @return something that works with Jackson.
     */
    public static AwtColor fromColor(Color color) {
        if (color == null) {
            return ORANGE;
        }
        // Check if it matches a named constant first
        for (AwtColor c : new AwtColor[]{WHITE, LIGHT_GRAY, GRAY, DARK_GRAY, BLACK,
            RED, PINK, ORANGE, YELLOW, GREEN, MAGENTA, CYAN, BLUE}) {
            if (c.color.getRGB() == color.getRGB()) {
                return c;
            }
        }
        // Fall back to hex representation
        int rgb = color.getRGB();
        int alpha = (rgb >> 24) & 0xFF;
        String hex = (alpha != 255)
                ? String.format("#%08X", rgb)
                : String.format("#%06X", rgb & 0xFFFFFF);
        return new AwtColor(color, hex);
    }

    public static AwtColor fromInt(int rgb) {
        return fromColor(new Color(rgb, true));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AwtColor other = (AwtColor) o;
        return color.getRGB() == other.color.getRGB();
    }

    @Override
    public int hashCode() {
        return color.hashCode();
    }

    @Override
    public String toString() {
        return representation;
    }
}
