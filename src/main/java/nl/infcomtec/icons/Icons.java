/*
 * Copyright (c) 2026 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.icons;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Curated standard subset of CCIDE's icon collection (nl.infcomtec.tools.Icons),
 * self-contained so callers who just want a handful of icons don't have to
 * pull in the full "tools" module. Grabs a file from resources that contains
 * icon definitions in SVG.
 */
public class Icons {

    private static final TreeMap<String, String> svg = new TreeMap<>();
    private static final TreeMap<String, Icon> icons = new TreeMap<>();
    private static final String header
            = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %1$d %1$d\" width=\"%1$d\" height=\"%1$d\">\n"
            + "  <rect width=\"%1$d\" height=\"%1$d\" fill=\"%4$s\"/>\n"
            + "  <g transform=\"translate(%2$d,%2$d) scale(%3$.4f)\">\n";

    private static void load() {
        try (BufferedReader bfr = new BufferedReader(new InputStreamReader(Icons.class.getResourceAsStream("/icons_svg"), StandardCharsets.UTF_8))) {
            StringBuilder icon = new StringBuilder();
            String name = null;
            for (String line = bfr.readLine(); line != null; line = bfr.readLine()) {
                if (line.startsWith("--")) {
                    if (null != name) {
                        add(name, icon.append("</g></svg>").toString());
                        icon.setLength(0);
                    }
                    name = line.substring(2).trim().toLowerCase();
                } else {
                    icon.append(line).append(System.lineSeparator());
                }
            }
            add(name, icon.append("</g></svg>").toString());
        } catch (Exception ex) {
            Logger.getLogger(Icons.class.getName()).log(Level.SEVERE, "SN:AFU, some screwed up resources?", ex);
            System.exit(2);
        }
    }

    public static List<String> getIconNames() {
        synchronized (svg) {
            if (svg.isEmpty()) {
                load();
            }
            return new ArrayList<>(svg.keySet());
        }
    }

    public static Icon getIcon(String name, int size, String backGround) {
        synchronized (svg) {
            if (svg.isEmpty()) {
                load();
            }
            if (!svg.containsKey(name)) {
                System.err.println("Icon " + name + " does not exist.");
                return null;
            }
            String key = String.format("%s_%d_%s", name, size, backGround);
            Icon src = icons.get(key);
            if (null != src) {
                return src;
            }
            File f = new File("/tmp", name + ".png");
            String fn = f.getAbsolutePath();
            try {
                Process p = new ProcessBuilder("convert", "-", fn)
                        .redirectErrorStream(true)
                        .start();
                p.getOutputStream().write((header.formatted(size, size / 2, size / 80.0, backGround) + svg.get(name))
                        .getBytes(StandardCharsets.UTF_8));
                p.getOutputStream().close();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int rc = p.waitFor();
                if (0 != rc) {
                    System.err.println(out);
                    return null;
                }
            } catch (Exception ex) {
                Logger.getLogger(Icons.class.getName()).log(Level.SEVERE, "convert failed", ex);
                return null;
            }
            Icon ret = new ImageIcon(fn);
            icons.put(key, ret);
            f.delete();
            return ret;
        }
    }

    private static void add(final String name, final String src) {
        StringBuilder uniq = new StringBuilder(name);
        while (svg.containsKey(uniq.toString())) {
            uniq.append("_dup");
        }
        svg.put(name, src);
    }
}
