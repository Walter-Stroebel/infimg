package nl.infcomtec.infimg.quadmesh;

/**
 * Apply a function to a quad's leaves.
 */
public interface QuadProcessor {

    /**
     * Called for each quad leaf.
     * @param q The leaf node.
     * @return True to continue processing, false to abort.
     */
    public boolean process(Quad q);
}
