package nl.infcomtec.infimg.quadmesh;

/**
 * Interface to define a criterionMet for a shape.
 *
 * @param <V> MatchType
 */
public interface ShapeCriterion<V> {

    /**
     * @param im Image to examine.
     * @param s Shape to examine.
     * @return Not null if the criterion is met (the region is uniform
     * enough to become a leaf); null to keep splitting.
     */
    V criterionMet(ImageSource im, java.awt.Shape s);
}
