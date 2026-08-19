package nl.infcomtec.infimg.quadmesh;

/**
 * Common surface Quad/ShapeCriterion need from an image. Deliberately
 * Lab-native (not returning an EnhancedColor) so a criterion never has to
 * reconvert RGB->Lab itself. Backed directly by EnhancedColor.getCIELAB
 * per call, no caching — infimg's own per-pixel CIELAB path
 * (ImageView.mapPerPixelLab) already has no cache and this shouldn't
 * introduce speculative caching either.
 */
public interface ImageSource {

    int getWidth();

    int getHeight();

    /**
     * @param lab out-param, filled with L*, a*, b* (unscaled) — same
     * calling convention as EnhancedColor.getCIELAB(int argb, double[] lab),
     * so the caller can reuse one array across a whole scan instead of
     * allocating per pixel.
     */
    void getLab(int x, int y, double[] lab);
}
