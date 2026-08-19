package nl.infcomtec.infimg.quadmesh;

import java.awt.image.BufferedImage;
import nl.infcomtec.infimg.EnhancedColor;

/**
 * {@link ImageSource} backed directly by a {@link BufferedImage}, reusing
 * infimg's existing {@link EnhancedColor#getCIELAB(int, double[])} rather
 * than building any Lab cache — see {@link ImageSource}'s doc for why.
 */
public class BufferedImageSource implements ImageSource {

    private final BufferedImage image;

    public BufferedImageSource(BufferedImage image) {
        this.image = image;
    }

    @Override
    public int getWidth() {
        return image.getWidth();
    }

    @Override
    public int getHeight() {
        return image.getHeight();
    }

    @Override
    public void getLab(int x, int y, double[] lab) {
        EnhancedColor.getCIELAB(image.getRGB(x, y), lab);
    }
}
